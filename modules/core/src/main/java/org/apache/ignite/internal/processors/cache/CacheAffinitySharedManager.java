/*
 * Copyright 2019 GridGain Systems, Inc. and Contributors.
 *
 * Licensed under the GridGain Community Edition License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.gridgain.com/products/software/community-edition/gridgain-community-edition-license
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.ignite.internal.processors.cache;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;
import javax.cache.CacheException;
import org.apache.ignite.IgniteCheckedException;
import org.apache.ignite.IgniteException;
import org.apache.ignite.IgniteSystemProperties;
import org.apache.ignite.cache.affinity.AffinityFunction;
import org.apache.ignite.cluster.ClusterNode;
import org.apache.ignite.configuration.CacheConfiguration;
import org.apache.ignite.configuration.NearCacheConfiguration;
import org.apache.ignite.events.DiscoveryEvent;
import org.apache.ignite.events.Event;
import org.apache.ignite.internal.IgniteInternalFuture;
import org.apache.ignite.internal.cluster.ClusterTopologyServerNotFoundException;
import org.apache.ignite.internal.events.DiscoveryCustomEvent;
import org.apache.ignite.internal.managers.discovery.DiscoCache;
import org.apache.ignite.internal.managers.discovery.DiscoveryCustomMessage;
import org.apache.ignite.internal.managers.eventstorage.GridLocalEventListener;
import org.apache.ignite.internal.processors.affinity.AffinityAssignment;
import org.apache.ignite.internal.processors.affinity.AffinityTopologyVersion;
import org.apache.ignite.internal.processors.affinity.GridAffinityAssignmentCache;
import org.apache.ignite.internal.processors.affinity.IdealAffinityAssignment;
import org.apache.ignite.internal.processors.cache.distributed.dht.ClientCacheDhtTopologyFuture;
import org.apache.ignite.internal.processors.cache.distributed.dht.GridDhtAffinityAssignmentResponse;
import org.apache.ignite.internal.processors.cache.distributed.dht.GridDhtAssignmentFetchFuture;
import org.apache.ignite.internal.processors.cache.distributed.dht.preloader.CacheGroupAffinityMessage;
import org.apache.ignite.internal.processors.cache.distributed.dht.preloader.GridDhtPartitionFullMap;
import org.apache.ignite.internal.processors.cache.distributed.dht.preloader.GridDhtPartitionMap;
import org.apache.ignite.internal.processors.cache.distributed.dht.preloader.GridDhtPartitionsExchangeFuture;
import org.apache.ignite.internal.processors.cache.distributed.dht.preloader.GridDhtPartitionsFullMessage;
import org.apache.ignite.internal.processors.cache.distributed.dht.topology.GridClientPartitionTopology;
import org.apache.ignite.internal.processors.cache.distributed.dht.topology.GridDhtPartitionState;
import org.apache.ignite.internal.processors.cache.distributed.dht.topology.GridDhtPartitionTopology;
import org.apache.ignite.internal.processors.cluster.DiscoveryDataClusterState;
import org.apache.ignite.internal.util.GridLongList;
import org.apache.ignite.internal.util.GridPartitionStateMap;
import org.apache.ignite.internal.util.future.GridCompoundFuture;
import org.apache.ignite.internal.util.future.GridFinishedFuture;
import org.apache.ignite.internal.util.future.GridFutureAdapter;
import org.apache.ignite.internal.util.lang.IgniteInClosureX;
import org.apache.ignite.internal.util.typedef.F;
import org.apache.ignite.internal.util.typedef.internal.CU;
import org.apache.ignite.internal.util.typedef.internal.U;
import org.apache.ignite.lang.IgniteClosure;
import org.apache.ignite.lang.IgniteFuture;
import org.apache.ignite.lang.IgniteInClosure;
import org.apache.ignite.lang.IgniteUuid;
import org.jetbrains.annotations.Nullable;

import static org.apache.ignite.cache.CacheMode.LOCAL;
import static org.apache.ignite.cache.CacheRebalanceMode.NONE;
import static org.apache.ignite.events.EventType.EVT_NODE_FAILED;
import static org.apache.ignite.events.EventType.EVT_NODE_JOINED;
import static org.apache.ignite.events.EventType.EVT_NODE_LEFT;

/**
 *
 */
@SuppressWarnings("ForLoopReplaceableByForEach")
public class CacheAffinitySharedManager<K, V> extends GridCacheSharedManagerAdapter<K, V> {
    /** */
    private final long clientCacheMsgTimeout =
        IgniteSystemProperties.getLong(IgniteSystemProperties.IGNITE_CLIENT_CACHE_CHANGE_MESSAGE_TIMEOUT, 10_000);

    /** */
    private static final IgniteClosure<ClusterNode, UUID> NODE_TO_ID = new IgniteClosure<ClusterNode, UUID>() {
        @Override public UUID apply(ClusterNode node) {
            return node.id();
        }
    };

    /** */
    private static final IgniteClosure<ClusterNode, Long> NODE_TO_ORDER = new IgniteClosure<ClusterNode, Long>() {
        @Override public Long apply(ClusterNode node) {
            return node.order();
        }
    };

    /** Affinity information for all started caches (initialized on coordinator). */
    private ConcurrentMap<Integer, CacheGroupHolder> grpHolders = new ConcurrentHashMap<>();

    /** Topology version which requires affinity re-calculation (set from discovery thread). */
    private AffinityTopologyVersion lastAffVer;

    /** Registered caches (updated from exchange thread). */
    private CachesRegistry cachesRegistry;

    /** */
    private WaitRebalanceInfo waitInfo;

    /** */
    private final Object mux = new Object();

    /** Pending affinity assignment futures. */
    private final ConcurrentMap<Long, GridDhtAssignmentFetchFuture> pendingAssignmentFetchFuts =
        new ConcurrentHashMap<>();

    /** */
    private final ThreadLocal<ClientCacheChangeDiscoveryMessage> clientCacheChanges = new ThreadLocal<>();

    /** Discovery listener. */
    private final GridLocalEventListener discoLsnr = new GridLocalEventListener() {
        @Override public void onEvent(Event evt) {
            DiscoveryEvent e = (DiscoveryEvent)evt;

            assert e.type() == EVT_NODE_LEFT || e.type() == EVT_NODE_FAILED;

            ClusterNode n = e.eventNode();

            for (GridDhtAssignmentFetchFuture fut : pendingAssignmentFetchFuts.values())
                fut.onNodeLeft(n.id());
        }
    };

    /** {@inheritDoc} */
    @Override protected void start0() throws IgniteCheckedException {
        super.start0();

        cctx.kernalContext().event().addLocalEventListener(discoLsnr, EVT_NODE_LEFT, EVT_NODE_FAILED);

        cachesRegistry = new CachesRegistry(cctx);
    }

    /**
     * Callback invoked from discovery thread when discovery message is received.
     *
     * @param type Event type.
     * @param customMsg Custom message instance.
     * @param node Event node.
     * @param topVer Topology version.
     * @param state Cluster state.
     */
    void onDiscoveryEvent(int type,
        @Nullable DiscoveryCustomMessage customMsg,
        ClusterNode node,
        AffinityTopologyVersion topVer,
        DiscoveryDataClusterState state) {
        if (type == EVT_NODE_JOINED && node.isLocal())
            lastAffVer = null;

        if ((state.transition() || !state.active()) &&
            !DiscoveryCustomEvent.requiresCentralizedAffinityAssignment(customMsg))
            return;

        if ((!node.isClient() && (type == EVT_NODE_FAILED || type == EVT_NODE_JOINED || type == EVT_NODE_LEFT)) ||
            DiscoveryCustomEvent.requiresCentralizedAffinityAssignment(customMsg)) {
            synchronized (mux) {
                assert lastAffVer == null || topVer.compareTo(lastAffVer) > 0 :
                    "lastAffVer=" + lastAffVer + ", topVer=" + topVer + ", customMsg=" + customMsg;

                lastAffVer = topVer;
            }
        }
    }

    /**
     * Must be called from exchange thread.
     */
    public IgniteInternalFuture<?> initCachesOnLocalJoin(
        Map<Integer, CacheGroupDescriptor> grpDescs,
        Map<String, DynamicCacheDescriptor> cacheDescs
    ) {
        return cachesRegistry.init(grpDescs, cacheDescs);
    }

    /**
     * Callback invoked from discovery thread when discovery custom message is received.
     *
     * @param msg Customer message.
     * @return {@code True} if minor topology version should be increased.
     */
    boolean onCustomEvent(CacheAffinityChangeMessage msg) {
        if (msg.exchangeId() != null) {
            if (log.isDebugEnabled()) {
                log.debug("Ignore affinity change message [lastAffVer=" + lastAffVer +
                    ", msgExchId=" + msg.exchangeId() +
                    ", msgVer=" + msg.topologyVersion() + ']');
            }

            return false;
        }

        // Skip message if affinity was already recalculated.
        boolean exchangeNeeded = lastAffVer == null || lastAffVer.equals(msg.topologyVersion());

        msg.exchangeNeeded(exchangeNeeded);

        if (exchangeNeeded) {
            if (log.isDebugEnabled()) {
                log.debug("Need process affinity change message [lastAffVer=" + lastAffVer +
                    ", msgExchId=" + msg.exchangeId() +
                    ", msgVer=" + msg.topologyVersion() + ']');
            }
        }
        else {
            if (log.isDebugEnabled()) {
                log.debug("Ignore affinity change message [lastAffVer=" + lastAffVer +
                    ", msgExchId=" + msg.exchangeId() +
                    ", msgVer=" + msg.topologyVersion() + ']');
            }
        }

        return exchangeNeeded;
    }

    /**
     * @param topVer Expected topology version.
     */
    private void onCacheGroupStopped(AffinityTopologyVersion topVer) {
        CacheAffinityChangeMessage msg = null;

        synchronized (mux) {
            if (waitInfo == null || !waitInfo.topVer.equals(topVer))
                return;

            if (waitInfo.waitGrps.isEmpty()) {
                msg = affinityChangeMessage(waitInfo);

                waitInfo = null;
            }
        }

        try {
            if (msg != null)
                cctx.discovery().sendCustomEvent(msg);
        }
        catch (IgniteCheckedException e) {
            U.error(log, "Failed to send affinity change message.", e);
        }
    }

    /**
     * @param top Topology.
     * @param checkGrpId Group ID.
     */
    void checkRebalanceState(GridDhtPartitionTopology top, Integer checkGrpId) {
        CacheAffinityChangeMessage msg = null;

        synchronized (mux) {
            if (waitInfo == null || !waitInfo.topVer.equals(lastAffVer) )
                return;

            Map<Integer, UUID> partWait = waitInfo.waitGrps.get(checkGrpId);

            boolean rebalanced = true;

            if (partWait != null) {
                CacheGroupHolder grpHolder = grpHolders.get(checkGrpId);

                if (grpHolder != null) {
                    for (Iterator<Map.Entry<Integer, UUID>> it = partWait.entrySet().iterator(); it.hasNext(); ) {
                        Map.Entry<Integer, UUID> e = it.next();

                        Integer part = e.getKey();
                        UUID waitNode = e.getValue();

                        GridDhtPartitionState state = top.partitionState(waitNode, part);

                        if (state != GridDhtPartitionState.OWNING) {
                            rebalanced = false;

                            break;
                        }
                        else
                            it.remove();
                    }
                }

                if (rebalanced) {
                    waitInfo.waitGrps.remove(checkGrpId);

                    if (waitInfo.waitGrps.isEmpty()) {
                        msg = affinityChangeMessage(waitInfo);

                        waitInfo = null;
                    }
                }
            }

            try {
                if (msg != null)
                    cctx.discovery().sendCustomEvent(msg);
            }
            catch (IgniteCheckedException e) {
                U.error(log, "Failed to send affinity change message.", e);
            }
        }
    }

    /**
     * @return Group IDs.
     */
    public Set<Integer> waitGroups() {
        synchronized (mux) {
            if (waitInfo == null || !waitInfo.topVer.equals(lastAffVer) )
                return Collections.emptySet();

            return new HashSet<>(waitInfo.waitGrps.keySet());
        }
    }

    /**
     * @param waitInfo Cache rebalance information.
     * @return Message.
     */
    @Nullable private CacheAffinityChangeMessage affinityChangeMessage(WaitRebalanceInfo waitInfo) {
        if (waitInfo.assignments.isEmpty()) // Possible if all awaited caches were destroyed.
            return null;

        Map<Integer, Map<Integer, List<UUID>>> assignmentsChange = U.newHashMap(waitInfo.assignments.size());

        for (Map.Entry<Integer, Map<Integer, List<ClusterNode>>> e : waitInfo.assignments.entrySet()) {
            Integer grpId = e.getKey();

            Map<Integer, List<ClusterNode>> assignment = e.getValue();

            Map<Integer, List<UUID>> assignment0 = U.newHashMap(assignment.size());

            for (Map.Entry<Integer, List<ClusterNode>> e0 : assignment.entrySet())
                assignment0.put(e0.getKey(), toIds0(e0.getValue()));

            assignmentsChange.put(grpId, assignment0);
        }

        return new CacheAffinityChangeMessage(waitInfo.topVer, assignmentsChange, waitInfo.deploymentIds);
    }

    /**
     * @param grp Cache group.
     */
    void onCacheGroupCreated(CacheGroupContext grp) {
        // no-op
    }

    /**
     * @param reqId Request ID.
     * @param startReqs Client cache start request.
     * @return Descriptors for caches to start.
     */
    @Nullable private List<DynamicCacheDescriptor> clientCachesToStart(
        UUID reqId,
        Map<String, DynamicCacheChangeRequest> startReqs
    ) {
        List<DynamicCacheDescriptor> startDescs = new ArrayList<>(startReqs.size());

        for (DynamicCacheChangeRequest startReq : startReqs.values()) {
            DynamicCacheDescriptor desc = cachesRegistry.cache(CU.cacheId(startReq.cacheName()));

            if (desc == null) {
                CacheException err = new CacheException("Failed to start client cache " +
                    "(a cache with the given name is not started): " + startReq.cacheName());

                cctx.cache().completeClientCacheChangeFuture(reqId, err);

                return null;
            }

            if (cctx.cacheContext(desc.cacheId()) != null)
                continue;

            startDescs.add(desc);
        }

        return startDescs;
    }

    /**
     * @param crd Coordinator flag.
     * @param msg Change request.
     * @param topVer Current topology version.
     * @param discoCache Discovery data cache.
     * @return Map of started caches (cache ID to near enabled flag).
     */
    @Nullable private Map<Integer, Boolean> processClientCacheStartRequests(
        boolean crd,
        ClientCacheChangeDummyDiscoveryMessage msg,
        AffinityTopologyVersion topVer,
        DiscoCache discoCache
    ) {
        Map<String, DynamicCacheChangeRequest> startReqs = msg.startRequests();

        List<DynamicCacheDescriptor> startDescs = clientCachesToStart(msg.requestId(), startReqs);

        if (startDescs == null || startDescs.isEmpty()) {
            cctx.cache().completeClientCacheChangeFuture(msg.requestId(), null);

            return null;
        }

        Map<Integer, GridDhtAssignmentFetchFuture> fetchFuts = U.newHashMap(startDescs.size());

        Map<Integer, Boolean> startedInfos = U.newHashMap(startDescs.size());

        List<StartCacheInfo> startCacheInfos = startDescs.stream()
            .map(desc -> {
                DynamicCacheChangeRequest changeReq = startReqs.get(desc.cacheName());

                startedInfos.put(desc.cacheId(), changeReq.nearCacheConfiguration() != null);

                return new StartCacheInfo(
                    desc.cacheConfiguration(),
                    desc,
                    changeReq.nearCacheConfiguration(),
                    topVer,
                    changeReq.disabledAfterStart(),
                    true
                );
            }).collect(Collectors.toList());

        Set<String> startedCaches = startCacheInfos.stream()
            .map(info -> info.getCacheDescriptor().cacheName())
            .collect(Collectors.toSet());

        try {
            cctx.cache().prepareStartCaches(startCacheInfos);
        }
        catch (IgniteCheckedException e) {
            cctx.cache().closeCaches(startedCaches, false);

            cctx.cache().completeClientCacheChangeFuture(msg.requestId(), e);

            return null;
        }

        Set<CacheGroupDescriptor> groupDescs = startDescs.stream()
            .map(DynamicCacheDescriptor::groupDescriptor)
            .collect(Collectors.toSet());

        for (CacheGroupDescriptor grpDesc : groupDescs) {
            try {
                CacheGroupContext grp = cctx.cache().cacheGroup(grpDesc.groupId());

                assert grp != null : grpDesc.groupId();
                assert !grp.affinityNode() || grp.isLocal() : grp.cacheOrGroupName();

                // Skip for local caches.
                if (grp.isLocal())
                    continue;

                CacheGroupHolder grpHolder = grpHolders.get(grp.groupId());

                assert !crd || (grpHolder != null && grpHolder.affinity().idealAssignmentRaw() != null);

                if (grpHolder == null)
                    grpHolder = getOrCreateGroupHolder(topVer, grpDesc);

                // If current node is not client and current node have no aff holder.
                if (grpHolder.nonAffNode() && !cctx.localNode().isClient()) {
                    ClientCacheDhtTopologyFuture topFut = new ClientCacheDhtTopologyFuture(topVer);

                    grp.topology().updateTopologyVersion(topFut, discoCache, -1, false);

                    grpHolder = new CacheGroupAffNodeHolder(grp, grpHolder.affinity());

                    grpHolders.put(grp.groupId(), grpHolder);

                    GridClientPartitionTopology clientTop = cctx.exchange().clearClientTopology(grp.groupId());

                    if (clientTop != null) {
                        grp.topology().update(
                            grpHolder.affinity().lastVersion(),
                            clientTop.partitionMap(true),
                            clientTop.fullUpdateCounters(),
                            Collections.<Integer>emptySet(),
                            null,
                            null
                        );
                    }

                    assert grpHolder.affinity().lastVersion().equals(grp.affinity().lastVersion());
                }
                else if (!crd && !fetchFuts.containsKey(grp.groupId())) {
                    boolean topVerLessOrNotInitialized = !grp.topology().initialized() ||
                        grp.topology().readyTopologyVersion().compareTo(topVer) < 0;

                    if (grp.affinity().lastVersion().compareTo(topVer) < 0 || topVerLessOrNotInitialized) {
                        GridDhtAssignmentFetchFuture fetchFut = new GridDhtAssignmentFetchFuture(cctx,
                            grp.groupId(),
                            topVer,
                            discoCache);

                        fetchFut.init(true);

                        fetchFuts.put(grp.groupId(), fetchFut);
                    }
                }
            }
            catch (IgniteCheckedException e) {
                cctx.cache().closeCaches(startedCaches, false);

                cctx.cache().completeClientCacheChangeFuture(msg.requestId(), e);

                return null;
            }
        }

        for (GridDhtAssignmentFetchFuture fetchFut : fetchFuts.values()) {
            try {
                CacheGroupContext grp = cctx.cache().cacheGroup(fetchFut.groupId());

                assert grp != null;

                GridDhtAffinityAssignmentResponse res = fetchAffinity(topVer,
                    null,
                    discoCache,
                    grp.affinity(),
                    fetchFut);

                GridDhtPartitionFullMap partMap;
                ClientCacheDhtTopologyFuture topFut;

                if (res != null) {
                    partMap = res.partitionMap();

                    assert partMap != null : res;

                    topFut = new ClientCacheDhtTopologyFuture(topVer);
                }
                else {
                    partMap = new GridDhtPartitionFullMap(cctx.localNodeId(), cctx.localNode().order(), 1);

                    topFut = new ClientCacheDhtTopologyFuture(topVer,
                        new ClusterTopologyServerNotFoundException("All server nodes left grid."));
                }

                grp.topology().updateTopologyVersion(topFut,
                    discoCache,
                    -1,
                    false);

                grp.topology().update(topVer, partMap, null, Collections.emptySet(), null, null);

                topFut.validate(grp, discoCache.allNodes());
            }
            catch (IgniteCheckedException e) {
                cctx.cache().closeCaches(startedCaches, false);

                cctx.cache().completeClientCacheChangeFuture(msg.requestId(), e);

                return null;
            }
        }

        for (DynamicCacheDescriptor desc : startDescs) {
            if (desc.cacheConfiguration().getCacheMode() != LOCAL) {
                CacheGroupContext grp = cctx.cache().cacheGroup(desc.groupId());

                assert grp != null;

                grp.topology().onExchangeDone(null, grp.affinity().cachedAffinity(topVer), true);
            }
        }

        cctx.cache().initCacheProxies(topVer, null);

        startReqs.keySet().forEach(req -> cctx.cache().completeProxyInitialize(req));

        cctx.cache().completeClientCacheChangeFuture(msg.requestId(), null);

        return startedInfos;
    }

    /**
     * @param msg Change request.
     * @param topVer Current topology version.
     * @param crd Coordinator flag.
     * @return Closed caches IDs.
     */
    private Set<Integer> processCacheCloseRequests(
        ClientCacheChangeDummyDiscoveryMessage msg,
        boolean crd,
        AffinityTopologyVersion topVer
    ) {
        Set<String> cachesToClose = msg.cachesToClose();

        Set<Integer> closed = cctx.cache().closeCaches(cachesToClose, true);

        for (CacheGroupHolder hld : grpHolders.values()) {
            if (!hld.nonAffNode() && cctx.cache().cacheGroup(hld.groupId()) == null) {
                int grpId = hld.groupId();

                // All client cache groups were stopped, need create 'client' CacheGroupHolder.
                CacheGroupHolder grpHolder = grpHolders.remove(grpId);

                assert grpHolder != null && !grpHolder.nonAffNode() : grpHolder;

                try {
                    grpHolder = createHolder(
                        cctx,
                        cachesRegistry.group(grpId),
                        topVer,
                        grpHolder.affinity()
                    );

                    grpHolders.put(grpId, grpHolder);
                }
                catch (IgniteCheckedException e) {
                    U.error(log, "Failed to initialize cache: " + e, e);
                }
            }
        }

        cctx.cache().completeClientCacheChangeFuture(msg.requestId(), null);

        return closed;
    }

    /**
     * Process non affinity node cache start/close requests, called from exchange thread.
     *
     * @param msg Change request.
     */
    void processClientCachesRequests(ClientCacheChangeDummyDiscoveryMessage msg) {
        // Get ready exchange version.
        AffinityTopologyVersion topVer = cctx.exchange().readyAffinityVersion();

        DiscoCache discoCache = cctx.discovery().discoCache(topVer);

        ClusterNode node = discoCache.oldestAliveServerNode();

        // Resolve coordinator for specific version.
        boolean crd = node != null && node.isLocal();

        Map<Integer, Boolean> startedCaches = null;
        Set<Integer> closedCaches = null;

        // Check and start caches via dummy message.
        if (msg.startRequests() != null)
            startedCaches = processClientCacheStartRequests(crd, msg, topVer, discoCache);

        // Check and close caches via dummy message.
        if (msg.cachesToClose() != null)
            closedCaches = processCacheCloseRequests(msg, crd, topVer);

        // Shedule change message.
        if (startedCaches != null || closedCaches != null)
            scheduleClientChangeMessage(startedCaches, closedCaches);
    }

    /**
     * Sends discovery message about started/closed client caches, called from exchange thread.
     *
     * @param timeoutObj Timeout object initiated send.
     */
    void sendClientCacheChangesMessage(ClientCacheUpdateTimeout timeoutObj) {
        ClientCacheChangeDiscoveryMessage msg = clientCacheChanges.get();

        // Timeout object was changed if one more client cache changed during timeout,
        // another timeoutObj was scheduled.
        if (msg != null && msg.updateTimeoutObject() == timeoutObj) {
            assert !msg.empty() : msg;

            clientCacheChanges.remove();

            msg.checkCachesExist(cachesRegistry.allCaches().keySet());

            try {
                if (!msg.empty())
                    cctx.discovery().sendCustomEvent(msg);
            }
            catch (IgniteCheckedException e) {
                U.error(log, "Failed to send discovery event: " + e, e);
            }
        }
    }

    /**
     * @param startedCaches Started caches.
     * @param closedCaches Closed caches.
     */
    private void scheduleClientChangeMessage(Map<Integer, Boolean> startedCaches, Set<Integer> closedCaches) {
        ClientCacheChangeDiscoveryMessage msg = clientCacheChanges.get();

        if (msg == null) {
            msg = new ClientCacheChangeDiscoveryMessage(startedCaches, closedCaches);

            clientCacheChanges.set(msg);
        }
        else {
            msg.merge(startedCaches, closedCaches);

            if (msg.empty()) {
                cctx.time().removeTimeoutObject(msg.updateTimeoutObject());

                clientCacheChanges.remove();

                return;
            }
        }

        if (msg.updateTimeoutObject() != null)
            cctx.time().removeTimeoutObject(msg.updateTimeoutObject());

        long timeout = clientCacheMsgTimeout;

        if (timeout <= 0)
            timeout = 10_000;

        ClientCacheUpdateTimeout timeoutObj = new ClientCacheUpdateTimeout(cctx, timeout);

        msg.updateTimeoutObject(timeoutObj);

        cctx.time().addTimeoutObject(timeoutObj);
    }

    /**
     * @param fut Exchange future.
     * @param crd Coordinator flag.
     * @param exchActions Exchange actions.
     */
    public void onCustomMessageNoAffinityChange(
        GridDhtPartitionsExchangeFuture fut,
        boolean crd,
        @Nullable final ExchangeActions exchActions
    ) {
        final ExchangeDiscoveryEvents evts = fut.context().events();

        forAllCacheGroups(crd, new IgniteInClosureX<GridAffinityAssignmentCache>() {
            @Override public void applyx(GridAffinityAssignmentCache aff) {
                if (exchActions != null && exchActions.cacheGroupStopping(aff.groupId()))
                    return;

                aff.clientEventTopologyChange(evts.lastEvent(), evts.topologyVersion());

                cctx.exchange().exchangerUpdateHeartbeat();
            }
        });
    }

    /**
     * @param cctx Stopped cache context.
     */
    public void stopCacheOnReconnect(GridCacheContext cctx) {
        cachesRegistry.unregisterCache(cctx.cacheId());
    }

    /**
     * @param grpCtx Stopped cache group context.
     */
    public void stopCacheGroupOnReconnect(CacheGroupContext grpCtx) {
        cachesRegistry.unregisterGroup(grpCtx.groupId());
    }

    /** {@inheritDoc} */
    @Override public void onDisconnected(IgniteFuture<?> reconnectFut) {
        Iterator<Integer> it = grpHolders.keySet().iterator();

        while (it.hasNext()) {
            int grpId = it.next();

            it.remove();

            cctx.io().removeHandler(true, grpId, GridDhtAffinityAssignmentResponse.class);
        }

        assert grpHolders.isEmpty();

        super.onDisconnected(reconnectFut);
    }

    /**
     * Called during the rollback of the exchange partitions procedure
     * in order to stop the given cache even if it's not fully initialized (e.g. failed on cache init stage).
     *
     * @param fut Exchange future.
     * @param crd Coordinator flag.
     * @param exchActions Cache change requests.
     */
    public void forceCloseCaches(
        GridDhtPartitionsExchangeFuture fut,
        boolean crd,
        final ExchangeActions exchActions
    ) {
        assert exchActions != null && !exchActions.empty() && exchActions.cacheStartRequests().isEmpty(): exchActions;

        IgniteInternalFuture<?> res = cachesRegistry.update(exchActions);

        assert res.isDone() : "There should be no caches to start: " + exchActions;

        processCacheStopRequests(fut, crd, exchActions, true);

        cctx.cache().forceCloseCaches(exchActions);
    }

    /**
     * Called on exchange initiated for cache start/stop request.
     *
     * @param fut Exchange future.
     * @param crd Coordinator flag.
     * @param exchActions Cache change requests.
     * @throws IgniteCheckedException If failed.
     */
    public IgniteInternalFuture<?> onCacheChangeRequest(
        GridDhtPartitionsExchangeFuture fut,
        boolean crd,
        final ExchangeActions exchActions
    ) throws IgniteCheckedException {
        assert exchActions != null && !exchActions.empty() : exchActions;

        IgniteInternalFuture<?> res = cachesRegistry.update(exchActions);

        // Affinity did not change for existing caches.
        onCustomMessageNoAffinityChange(fut, crd, exchActions);

        fut.timeBag().finishGlobalStage("Update caches registry");

        processCacheStartRequests(fut, crd, exchActions);

        Set<Integer> stoppedGrps = processCacheStopRequests(fut, crd, exchActions, false);

        if (stoppedGrps != null) {
            AffinityTopologyVersion notifyTopVer = null;

            synchronized (mux) {
                if (waitInfo != null) {
                    for (Integer grpId : stoppedGrps) {
                        boolean rmv = waitInfo.waitGrps.remove(grpId) != null;

                        if (rmv) {
                            notifyTopVer = waitInfo.topVer;

                            waitInfo.assignments.remove(grpId);
                        }
                    }
                }
            }

            if (notifyTopVer != null) {
                final AffinityTopologyVersion topVer = notifyTopVer;

                cctx.kernalContext().closure().runLocalSafe(new Runnable() {
                    @Override public void run() {
                        onCacheGroupStopped(topVer);
                    }
                });
            }
        }

        ClientCacheChangeDiscoveryMessage msg = clientCacheChanges.get();

        if (msg != null) {
            msg.checkCachesExist(cachesRegistry.allCaches().keySet());

            if (msg.empty())
                clientCacheChanges.remove();
        }

        return res;
    }

    /**
     * Process cache start requests.
     *
     * @param fut Exchange future.
     * @param crd Coordinator flag.
     * @param exchActions Cache change requests.
     * @throws IgniteCheckedException If failed.
     */
    private void processCacheStartRequests(
        GridDhtPartitionsExchangeFuture fut,
        boolean crd,
        final ExchangeActions exchActions
    ) throws IgniteCheckedException {
        assert exchActions != null && !exchActions.empty() : exchActions;

        final ExchangeDiscoveryEvents evts = fut.context().events();

        Map<StartCacheInfo, DynamicCacheChangeRequest> startCacheInfos = new LinkedHashMap<>();

        for (ExchangeActions.CacheActionData action : exchActions.cacheStartRequests()) {
            DynamicCacheDescriptor cacheDesc = action.descriptor();

            DynamicCacheChangeRequest req = action.request();

            boolean startCache;

            NearCacheConfiguration nearCfg = null;

            if (req.locallyConfigured() || (cctx.localNodeId().equals(req.initiatingNodeId()) && !exchActions.activate())) {
                startCache = true;

                nearCfg = req.nearCacheConfiguration();
            }
            else {
                // Cache should not be started
                assert cctx.cacheContext(cacheDesc.cacheId()) == null
                    : "Starting cache has not null context: " + cacheDesc.cacheName();

                IgniteCacheProxyImpl cacheProxy = cctx.cache().jcacheProxy(req.cacheName(), false);

                // If it has proxy then try to start it
                if (cacheProxy != null) {
                    // Cache should be in restarting mode
                    assert cacheProxy.isRestarting()
                        : "Cache has non restarting proxy " + cacheProxy;

                    startCache = true;
                }
                else {
                    startCache = CU.affinityNode(cctx.localNode(),
                        cacheDesc.groupDescriptor().config().getNodeFilter());
                }
            }

            if (startCache) {
                startCacheInfos.put(
                    new StartCacheInfo(
                        req.startCacheConfiguration(),
                        cacheDesc,
                        nearCfg,
                        evts.topologyVersion(),
                        req.disabledAfterStart()
                    ),
                    req
                );
            }
            else
                cctx.kernalContext().cache().initQueryStructuresForNotStartedCache(cacheDesc);
        }

        Map<StartCacheInfo, IgniteCheckedException> failedCaches = cctx.cache().prepareStartCachesIfPossible(startCacheInfos.keySet());

        for (Map.Entry<StartCacheInfo, IgniteCheckedException> entry : failedCaches.entrySet()) {
            if (cctx.localNode().isClient()) {
                U.error(log, "Failed to initialize cache. Will try to rollback cache start routine. " +
                    "[cacheName=" + entry.getKey().getStartedConfiguration().getName() + ']', entry.getValue());

                cctx.cache().closeCaches(Collections.singleton(entry.getKey().getStartedConfiguration().getName()), false);

                cctx.cache().completeCacheStartFuture(startCacheInfos.get(entry.getKey()), false, entry.getValue());
            }
            else
                throw entry.getValue();
        }

        Set<StartCacheInfo> failedCacheInfos = failedCaches.keySet();

        List<StartCacheInfo> cacheInfos = startCacheInfos.keySet().stream()
            .filter(failedCacheInfos::contains)
            .collect(Collectors.toList());

        for (StartCacheInfo info : cacheInfos) {
            if (fut.cacheAddedOnExchange(info.getCacheDescriptor().cacheId(), info.getCacheDescriptor().receivedFrom())) {
                if (fut.events().discoveryCache().cacheGroupAffinityNodes(info.getCacheDescriptor().groupId()).isEmpty())
                    U.quietAndWarn(log, "No server nodes found for cache client: " + info.getCacheDescriptor().cacheName());
            }
        }

        fut.timeBag().finishGlobalStage("Start caches");

        initAffinityOnCacheGroupsStart(fut, exchActions, crd);

        fut.timeBag().finishGlobalStage("Affinity initialization on cache group start");
    }

    /**
     * Initializes affinity for started cache groups received during {@code fut}.
     *
     * @param fut Exchange future.
     * @param exchangeActions Exchange actions.
     * @param crd {@code True} if local node is coordinator.
     */
    private void initAffinityOnCacheGroupsStart(
        GridDhtPartitionsExchangeFuture fut,
        ExchangeActions exchangeActions,
        boolean crd
    ) throws IgniteCheckedException {
        List<CacheGroupDescriptor> startedGroups = exchangeActions.cacheStartRequests().stream()
            .map(action -> action.descriptor().groupDescriptor())
            .distinct()
            .collect(Collectors.toList());

        U.doInParallel(
            cctx.kernalContext().getSystemExecutorService(),
            startedGroups,
            grpDesc -> {
                initStartedGroup(fut, grpDesc, crd);

                fut.timeBag().finishLocalStage("Affinity initialization on cache group start " +
                    "[grp=" + grpDesc.cacheOrGroupName() + "]");

                return null;
            }
        );
    }

    /**
     * Process cache stop requests.
     *
     * @param fut Exchange future.
     * @param crd Coordinator flag.
     * @param exchActions Cache change requests.
     * @param forceClose Force close flag.
     * @return Set of cache groups to be stopped.
     */
    private Set<Integer> processCacheStopRequests(
        GridDhtPartitionsExchangeFuture fut,
        boolean crd,
        final ExchangeActions exchActions,
        boolean forceClose
    ) {
        assert exchActions != null && !exchActions.empty() : exchActions;

        for (ExchangeActions.CacheActionData action : exchActions.cacheStopRequests())
            cctx.cache().blockGateway(action.request().cacheName(), true, action.request().restart());

        for (ExchangeActions.CacheGroupActionData action : exchActions.cacheGroupsToStop())
            cctx.exchange().clearClientTopology(action.descriptor().groupId());

        Set<Integer> stoppedGrps = null;

        for (ExchangeActions.CacheGroupActionData data : exchActions.cacheGroupsToStop()) {
            if (data.descriptor().config().getCacheMode() != LOCAL) {
                CacheGroupHolder cacheGrp = grpHolders.remove(data.descriptor().groupId());

                assert !crd || (cacheGrp != null || forceClose) : data.descriptor();

                if (cacheGrp != null) {
                    if (stoppedGrps == null)
                        stoppedGrps = new HashSet<>();

                    stoppedGrps.add(cacheGrp.groupId());

                    cctx.io().removeHandler(true, cacheGrp.groupId(), GridDhtAffinityAssignmentResponse.class);
                }
            }
        }

        return stoppedGrps;
    }

    /**
     *
     */
    public void clearGroupHoldersAndRegistry() {
        grpHolders.clear();

        cachesRegistry.unregisterAll();
    }

    /**
     * Called when received {@link CacheAffinityChangeMessage} which should complete exchange.
     *
     * @param exchFut Exchange future.
     * @param crd Coordinator flag.
     * @param msg Affinity change message.
     */
    public void onExchangeChangeAffinityMessage(
        GridDhtPartitionsExchangeFuture exchFut,
        boolean crd,
        CacheAffinityChangeMessage msg
    ) {
        if (log.isDebugEnabled()) {
            log.debug("Process exchange affinity change message [exchVer=" + exchFut.initialVersion() +
                ", msg=" + msg + ']');
        }

        assert exchFut.exchangeId().equals(msg.exchangeId()) : msg;

        final AffinityTopologyVersion topVer = exchFut.initialVersion();

        final Map<Integer, Map<Integer, List<UUID>>> assignment = msg.assignmentChange();

        assert assignment != null;

        final Map<Object, List<List<ClusterNode>>> affCache = new ConcurrentHashMap<>();

        forAllCacheGroups(crd, new IgniteInClosureX<GridAffinityAssignmentCache>() {
            @Override public void applyx(GridAffinityAssignmentCache aff) throws IgniteCheckedException {
                List<List<ClusterNode>> idealAssignment = aff.idealAssignmentRaw();

                assert idealAssignment != null;

                Map<Integer, List<UUID>> cacheAssignment = assignment.get(aff.groupId());

                List<List<ClusterNode>> newAssignment;

                if (cacheAssignment != null) {
                    newAssignment = new ArrayList<>(idealAssignment);

                    for (Map.Entry<Integer, List<UUID>> e : cacheAssignment.entrySet())
                        newAssignment.set(e.getKey(), toNodes(topVer, e.getValue()));
                }
                else
                    newAssignment = idealAssignment;

                aff.initialize(topVer, newAssignment);

                exchFut.timeBag().finishLocalStage("Affinity recalculate by change affinity message " +
                    "[grp=" + aff.cacheOrGroupName() + "]");
            }
        });
    }

    /**
     * Called on exchange initiated by {@link CacheAffinityChangeMessage} which sent after rebalance finished.
     *
     * @param exchFut Exchange future.
     * @param crd Coordinator flag.
     * @param msg Message.
     * @throws IgniteCheckedException If failed.
     */
    public void onChangeAffinityMessage(
        final GridDhtPartitionsExchangeFuture exchFut,
        boolean crd,
        final CacheAffinityChangeMessage msg
    ) {
        assert msg.topologyVersion() != null && msg.exchangeId() == null : msg;

        final AffinityTopologyVersion topVer = exchFut.initialVersion();

        if (log.isDebugEnabled()) {
            log.debug("Process affinity change message [exchVer=" + topVer +
                ", msgVer=" + msg.topologyVersion() + ']');
        }

        final Map<Integer, Map<Integer, List<UUID>>> affChange = msg.assignmentChange();

        assert !F.isEmpty(affChange) : msg;

        final Map<Integer, IgniteUuid> deploymentIds = msg.cacheDeploymentIds();

        final Map<Object, List<List<ClusterNode>>> affCache = new ConcurrentHashMap<>();

        forAllCacheGroups(crd, new IgniteInClosureX<GridAffinityAssignmentCache>() {
            @Override public void applyx(GridAffinityAssignmentCache aff) throws IgniteCheckedException {
                AffinityTopologyVersion affTopVer = aff.lastVersion();

                assert affTopVer.topologyVersion() > 0 : affTopVer;

                CacheGroupDescriptor desc = cachesRegistry.group(aff.groupId());

                assert desc != null : aff.cacheOrGroupName();

                IgniteUuid deploymentId = desc.deploymentId();

                if (!deploymentId.equals(deploymentIds.get(aff.groupId()))) {
                    aff.clientEventTopologyChange(exchFut.firstEvent(), topVer);

                    return;
                }

                Map<Integer, List<UUID>> change = affChange.get(aff.groupId());

                if (change != null) {
                    assert !change.isEmpty() : msg;

                    List<List<ClusterNode>> curAff = aff.assignments(affTopVer);

                    List<List<ClusterNode>> assignment = new ArrayList<>(curAff);

                    for (Map.Entry<Integer, List<UUID>> e : change.entrySet()) {
                        Integer part = e.getKey();

                        List<ClusterNode> nodes = toNodes(topVer, e.getValue());

                        assert !nodes.equals(assignment.get(part)) : "Assignment did not change " +
                            "[cacheGrp=" + aff.cacheOrGroupName() +
                            ", part=" + part +
                            ", cur=" + F.nodeIds(assignment.get(part)) +
                            ", new=" + F.nodeIds(nodes) +
                            ", exchVer=" + exchFut.initialVersion() +
                            ", msgVer=" + msg.topologyVersion() +
                            ']';

                        assignment.set(part, nodes);
                    }

                    aff.initialize(topVer, assignment);
                }
                else
                    aff.clientEventTopologyChange(exchFut.firstEvent(), topVer);

                cctx.exchange().exchangerUpdateHeartbeat();

                exchFut.timeBag().finishLocalStage("Affinity change by custom message " +
                    "[grp=" + aff.cacheOrGroupName() + "]");
            }
        });
    }

    /**
     * Called on exchange initiated by client node join/fail.
     *
     * @param fut Exchange future.
     * @param crd Coordinator flag.
     * @throws IgniteCheckedException If failed.
     */
    public void onClientEvent(final GridDhtPartitionsExchangeFuture fut, boolean crd) throws IgniteCheckedException {
        boolean locJoin = fut.firstEvent().eventNode().isLocal();

        if (!locJoin) {
            forAllCacheGroups(crd, new IgniteInClosureX<GridAffinityAssignmentCache>() {
                @Override public void applyx(GridAffinityAssignmentCache aff) throws IgniteCheckedException {
                    AffinityTopologyVersion topVer = fut.initialVersion();

                    aff.clientEventTopologyChange(fut.firstEvent(), topVer);

                    cctx.exchange().exchangerUpdateHeartbeat();
                }
            });
        }
        else
            fetchAffinityOnJoin(fut);
    }

    /**
     * @param fut Future to add.
     */
    public void addDhtAssignmentFetchFuture(GridDhtAssignmentFetchFuture fut) {
        GridDhtAssignmentFetchFuture old = pendingAssignmentFetchFuts.putIfAbsent(fut.id(), fut);

        assert old == null : "More than one thread is trying to fetch partition assignments [fut=" + fut +
            ", allFuts=" + pendingAssignmentFetchFuts + ']';
    }

    /**
     * @param fut Future to remove.
     */
    public void removeDhtAssignmentFetchFuture(GridDhtAssignmentFetchFuture fut) {
        boolean rmv = pendingAssignmentFetchFuts.remove(fut.id(), fut);

        assert rmv : "Failed to remove assignment fetch future: " + fut.id();
    }

    /**
     * @param nodeId Node ID.
     * @param res Response.
     */
    private void processAffinityAssignmentResponse(UUID nodeId, GridDhtAffinityAssignmentResponse res) {
        if (log.isDebugEnabled())
            log.debug("Processing affinity assignment response [node=" + nodeId + ", res=" + res + ']');

        GridDhtAssignmentFetchFuture fut = pendingAssignmentFetchFuts.get(res.futureId());

        if (fut != null)
            fut.onResponse(nodeId, res);
    }

    /**
     * @param c Cache closure.
     */
    private void forAllRegisteredCacheGroups(IgniteInClosureX<CacheGroupDescriptor> c) {
        Collection<CacheGroupDescriptor> affinityCaches = cachesRegistry.allGroups().values().stream()
            .filter(desc -> desc.config().getCacheMode() != LOCAL)
            .collect(Collectors.toList());

        try {
            U.doInParallel(cctx.kernalContext().getSystemExecutorService(), affinityCaches, t -> {
                c.applyx(t);

                return null;
            });
        }
        catch (IgniteCheckedException e) {
            throw new IgniteException("Failed to execute affinity operation on cache groups", e);
        }
    }

    /**
     * @param crd Coordinator flag.
     * @param c Closure.
     */
    private void forAllCacheGroups(boolean crd, IgniteInClosureX<GridAffinityAssignmentCache> c) {
        Collection<GridAffinityAssignmentCache> affinityCaches;

        Collection<GridAffinityAssignmentCache> affinityCaches1 = grpHolders.values().stream()
            .map(CacheGroupHolder::affinity)
            .collect(Collectors.toList());

        Collection<GridAffinityAssignmentCache> affinityCaches2 = cctx.kernalContext().cache().cacheGroups().stream()
            .filter(grp -> !grp.isLocal())
            .filter(grp -> !grp.isRecoveryMode())
            .map(CacheGroupContext::affinity)
            .collect(Collectors.toList());

        if (!cctx.localNode().isClient())
            affinityCaches = affinityCaches1;
        else
            affinityCaches = affinityCaches2;

        try {
            U.doInParallel(cctx.kernalContext().getSystemExecutorService(), affinityCaches, t -> {
                c.applyx(t);

                return null;
            });
        }
        catch (IgniteCheckedException e) {
            throw new IgniteException("Failed to execute affinity operation on cache groups", e);
        }
    }

    /**
     * @param fut Exchange future.
     * @param grpDesc Cache group descriptor.
     * @throws IgniteCheckedException If failed.
     */
    private void initStartedGroup(GridDhtPartitionsExchangeFuture fut, final CacheGroupDescriptor grpDesc, boolean crd)
        throws IgniteCheckedException {
        assert grpDesc != null && grpDesc.groupId() != 0 : grpDesc;

        if (grpDesc.config().getCacheMode() == LOCAL)
            return;

        int grpId = grpDesc.groupId();

        CacheGroupHolder grpHolder = grpHolders.get(grpId);

        CacheGroupContext grp = cctx.kernalContext().cache().cacheGroup(grpId);

        if (grpHolder != null && grpHolder.nonAffNode() && grp != null) {
            assert grpHolder.affinity().idealAssignmentRaw() != null;

            grpHolder = new CacheGroupAffNodeHolder(grp, grpHolder.affinity());

            grpHolders.put(grpId, grpHolder);
        }
        else if (grpHolder == null) {
            grpHolder = getOrCreateGroupHolder(fut.initialVersion(), grpDesc);

            calculateAndInit(fut.events(), grpHolder.affinity(), fut.initialVersion());
        }
        else if (!crd && grp != null && grp.localStartVersion().equals(fut.initialVersion()))
            initAffinity(cachesRegistry.group(grp.groupId()), grp.affinity(), fut);
    }

    /**
     * Initialized affinity for cache received from node joining on this exchange.
     *
     * @param crd Coordinator flag.
     * @param fut Exchange future.
     * @param descs Cache descriptors.
     * @throws IgniteCheckedException If failed.
     */
    public IgniteInternalFuture<?> initStartedCaches(
        boolean crd,
        final GridDhtPartitionsExchangeFuture fut,
        Collection<DynamicCacheDescriptor> descs
    ) throws IgniteCheckedException {
        IgniteInternalFuture<?> res = cachesRegistry.addUnregistered(descs);

        if (fut.context().mergeExchanges())
            return res;

        forAllRegisteredCacheGroups(new IgniteInClosureX<CacheGroupDescriptor>() {
            @Override public void applyx(CacheGroupDescriptor desc) throws IgniteCheckedException {
                CacheGroupHolder cache = getOrCreateGroupHolder(fut.initialVersion(), desc);

                if (cache.affinity().lastVersion().equals(AffinityTopologyVersion.NONE)) {
                    initAffinity(desc, cache.affinity(), fut);

                    cctx.exchange().exchangerUpdateHeartbeat();

                    fut.timeBag().finishLocalStage("Affinity initialization (new cache) " +
                        "[grp=" + desc.cacheOrGroupName() + ", crd=" + crd + "]");
                }
            }
        });

        return res;
    }

    /**
     * @param desc Cache group descriptor.
     * @param aff Affinity.
     * @param fut Exchange future.
     * @throws IgniteCheckedException If failed.
     */
    private void initAffinity(CacheGroupDescriptor desc,
        GridAffinityAssignmentCache aff,
        GridDhtPartitionsExchangeFuture fut)
        throws IgniteCheckedException {
        assert desc != null : aff.cacheOrGroupName();

        ExchangeDiscoveryEvents evts = fut.context().events();

        if (canCalculateAffinity(desc, aff, fut))
            calculateAndInit(evts, aff, evts.topologyVersion());
        else {
            GridDhtAssignmentFetchFuture fetchFut = new GridDhtAssignmentFetchFuture(cctx,
                desc.groupId(),
                evts.topologyVersion(),
                evts.discoveryCache());

            fetchFut.init(false);

            fetchAffinity(evts.topologyVersion(),
                evts,
                evts.discoveryCache(),
                aff,
                fetchFut);
        }
    }

    /**
     * @param desc Cache group descriptor.
     * @param aff Affinity.
     * @param fut Exchange future.
     * @return {@code True} if local node can calculate affinity on it's own for this partition map exchange.
     */
    private boolean canCalculateAffinity(CacheGroupDescriptor desc,
        GridAffinityAssignmentCache aff,
        GridDhtPartitionsExchangeFuture fut) {
        assert desc != null : aff.cacheOrGroupName();

        // Do not request affinity from remote nodes if affinity function is not centralized.
        if (!aff.centralizedAffinityFunction())
            return true;

        // If local node did not initiate exchange or local node is the only cache node in grid.
        Collection<ClusterNode> affNodes = fut.events().discoveryCache().cacheGroupAffinityNodes(aff.groupId());

        return fut.cacheGroupAddedOnExchange(aff.groupId(), desc.receivedFrom()) ||
            !fut.exchangeId().nodeId().equals(cctx.localNodeId()) ||
            (affNodes.isEmpty() || (affNodes.size() == 1 && affNodes.contains(cctx.localNode())));
    }

    /**
     * @param grpId Cache group ID.
     * @return Affinity assignments.
     */
    public GridAffinityAssignmentCache affinity(Integer grpId) {
        CacheGroupHolder grpHolder = grpHolders.get(grpId);

        assert grpHolder != null : debugGroupName(grpId);

        return grpHolder.affinity();
    }

    /**
     * Applies affinity diff from the received full message.
     *
     * @param fut Current exchange future.
     * @param msg Finish exchange message.
     */
    public void applyAffinityFromFullMessage(
        final GridDhtPartitionsExchangeFuture fut,
        final GridDhtPartitionsFullMessage msg
    ) {
        // Please do not use following pattern of code (nodesByOrder, affCache). NEVER.
        final Map<Long, ClusterNode> nodesByOrder = new ConcurrentHashMap<>();

        forAllCacheGroups(false, new IgniteInClosureX<GridAffinityAssignmentCache>() {
            @Override public void applyx(GridAffinityAssignmentCache aff) throws IgniteCheckedException {
                ExchangeDiscoveryEvents evts = fut.context().events();

                Map<Integer, CacheGroupAffinityMessage> idealAffDiff = msg.idealAffinityDiff();

                List<List<ClusterNode>> idealAssignment = aff.calculate(evts.topologyVersion(), evts, evts.discoveryCache()).assignment();

                CacheGroupAffinityMessage affMsg = idealAffDiff != null ? idealAffDiff.get(aff.groupId()) : null;

                List<List<ClusterNode>> newAssignment;

                if (affMsg != null) {
                    Map<Integer, GridLongList> diff = affMsg.assignmentsDiff();

                    assert !F.isEmpty(diff);

                    newAssignment = new ArrayList<>(idealAssignment);

                    for (Map.Entry<Integer, GridLongList> e : diff.entrySet()) {
                        GridLongList assign = e.getValue();

                        newAssignment.set(e.getKey(), CacheGroupAffinityMessage.toNodes(assign,
                            nodesByOrder,
                            evts.discoveryCache()));
                    }
                }
                else
                    newAssignment = idealAssignment;

                aff.initialize(evts.topologyVersion(), newAssignment);

                fut.timeBag().finishLocalStage("Affinity applying from full message " +
                    "[grp=" + aff.cacheOrGroupName() + "]");
            }
        });
    }

    /**
     * @param fut Current exchange future.
     * @param msg Message finish message.
     * @param resTopVer Result topology version.
     */
    public void onLocalJoin(
        final GridDhtPartitionsExchangeFuture fut,
        GridDhtPartitionsFullMessage msg,
        final AffinityTopologyVersion resTopVer
    ) {
        final Set<Integer> affReq = fut.context().groupsAffinityRequestOnJoin();

        final Map<Integer, CacheGroupAffinityMessage> receivedAff = msg.joinedNodeAffinity();

        assert F.isEmpty(affReq) || (!F.isEmpty(receivedAff) && receivedAff.size() >= affReq.size())
            : ("Requested and received affinity are different " +
            "[requestedCnt=" + (affReq != null ? affReq.size() : "none") +
            ", receivedCnt=" + (receivedAff != null ? receivedAff.size() : "none") +
            ", msg=" + msg + "]");

        final Map<Long, ClusterNode> nodesByOrder = new ConcurrentHashMap<>();

        forAllRegisteredCacheGroups(new IgniteInClosureX<CacheGroupDescriptor>() {
            @Override public void applyx(CacheGroupDescriptor desc) throws IgniteCheckedException {
                ExchangeDiscoveryEvents evts = fut.context().events();

                CacheGroupHolder holder = getOrCreateGroupHolder(fut.initialVersion(), desc);

                GridAffinityAssignmentCache aff = holder.affinity();

                CacheGroupContext grp = cctx.cache().cacheGroup(holder.groupId());

                if (affReq != null && affReq.contains(aff.groupId())) {
                    assert resTopVer.compareTo(aff.lastVersion()) >= 0 : aff.lastVersion();

                    CacheGroupAffinityMessage affMsg = receivedAff.get(aff.groupId());

                    assert affMsg != null;

                    List<List<ClusterNode>> assignments = affMsg.createAssignments(nodesByOrder, evts.discoveryCache());

                    assert resTopVer.equals(evts.topologyVersion()) : "resTopVer=" + resTopVer +
                        ", evts.topVer=" + evts.topologyVersion();

                    List<List<ClusterNode>> idealAssign =
                        affMsg.createIdealAssignments(nodesByOrder, evts.discoveryCache());

                    if (idealAssign != null)
                        aff.idealAssignment(evts.topologyVersion(), idealAssign);
                    else {
                        assert !aff.centralizedAffinityFunction() : aff;

                        // Calculate ideal assignments.
                        aff.calculate(evts.topologyVersion(), evts, evts.discoveryCache());
                    }

                    aff.initialize(evts.topologyVersion(), assignments);
                }
                else if (grp != null && fut.cacheGroupAddedOnExchange(aff.groupId(), grp.receivedFrom()))
                    calculateAndInit(evts, aff, evts.topologyVersion());

                if (grp != null)
                    grp.topology().initPartitionsWhenAffinityReady(resTopVer, fut);

                fut.timeBag().finishLocalStage("Affinity initialization (local join) " +
                    "[grp=" + aff.cacheOrGroupName() + "]");
            }
        });
    }

    /**
     * @param fut Current exchange future.
     * @param crd Coordinator flag.
     * @throws IgniteCheckedException If failed.
     */
    public void onServerJoinWithExchangeMergeProtocol(GridDhtPartitionsExchangeFuture fut, boolean crd)
        throws IgniteCheckedException {
        final ExchangeDiscoveryEvents evts = fut.context().events();

        assert fut.context().mergeExchanges();
        assert evts.hasServerJoin() && !evts.hasServerLeft();

        WaitRebalanceInfo waitRebalanceInfo = initAffinityOnNodeJoin(fut, crd);

        this.waitInfo = waitRebalanceInfo != null && !waitRebalanceInfo.empty() ? waitRebalanceInfo : null;

        WaitRebalanceInfo info = this.waitInfo;

        if (crd) {
            if (log.isDebugEnabled()) {
                log.debug("Computed new affinity after node join [topVer=" + evts.topologyVersion() +
                    ", waitGrps=" + (info != null ? groupNames(info.waitGrps.keySet()) : null) + ']');
            }
        }
    }

    /**
     * @param fut Current exchange future.
     * @return Computed difference with ideal affinity.
     * @throws IgniteCheckedException If failed.
     */
    public Map<Integer, CacheGroupAffinityMessage> onServerLeftWithExchangeMergeProtocol(
        final GridDhtPartitionsExchangeFuture fut) throws IgniteCheckedException
    {
        final ExchangeDiscoveryEvents evts = fut.context().events();

        assert fut.context().mergeExchanges();
        assert evts.hasServerLeft();

        if (evts.hasServerLeft() && evts.hasServerJoin())
            return onReassignmentEnforced(fut);
        else
            return onServerLeftWithExchangeMergeProtocolLightweight(fut);
    }

    /**
     * @param fut Current exchange future.
     * @return Computed difference with ideal affinity.
     * @throws IgniteCheckedException If failed.
     */
    private Map<Integer, CacheGroupAffinityMessage> onServerLeftWithExchangeMergeProtocolLightweight(
        final GridDhtPartitionsExchangeFuture fut) throws IgniteCheckedException
    {
        final ExchangeDiscoveryEvents evts = fut.context().events();
        final AffinityTopologyVersion topVer = evts.topologyVersion();

        assert fut.context().mergeExchanges();
        assert evts.hasServerLeft();

        final WaitRebalanceInfo waitRebalanceInfo =
            new WaitRebalanceInfo(fut.context().events().lastServerEventVersion());

        final Map<Integer, Map<Integer, List<Long>>> diff = new ConcurrentHashMap<>();

        final Set<ClusterNode> aliveNodes = new HashSet<>(fut.context().events().discoveryCache().serverNodes());

        forAllRegisteredCacheGroups(new IgniteInClosureX<CacheGroupDescriptor>() {
            @Override public void applyx(CacheGroupDescriptor desc) throws IgniteCheckedException {
                AffinityTopologyVersion topVer = evts.topologyVersion();

                CacheGroupHolder grpHolder = getOrCreateGroupHolder(topVer, desc);

                AffinityTopologyVersion affTopVer = grpHolder.affinity().lastVersion();

                // Already calculated.
                if (affTopVer.equals(topVer))
                    return;

                IdealAffinityAssignment idealAssignment = grpHolder.affinity().calculate(topVer, evts, evts.discoveryCache());

                if (!grpHolder.rebalanceEnabled || fut.cacheGroupAddedOnExchange(desc.groupId(), desc.receivedFrom())) {
                    grpHolder.affinity().initialize(topVer, idealAssignment.assignment());

                    return;
                }

                List<List<ClusterNode>> curAssignment = grpHolder.affinity().assignments(affTopVer);

                assert curAssignment != null;

                List<List<ClusterNode>> newAssignment = new ArrayList<>(idealAssignment.assignment());

                GridDhtPartitionTopology top = grpHolder.topology(fut.context().events().discoveryCache());

                BitSet processedPartitions = new BitSet(curAssignment.size());

                Map<Integer, List<Long>> cacheAffinityDiff = new HashMap<>();

                for (ClusterNode leftNode : evts.leftServerNodes()) {
                    for (int p : idealAssignment.idealPrimaries(leftNode)) {
                        List<ClusterNode> curOwners = curAssignment.get(p);

                        if (curOwners.isEmpty())
                            continue;

                        List<ClusterNode> idealOwners = idealAssignment.assignment().get(p);

                        List<ClusterNode> newOwners = null;

                        if (idealOwners.isEmpty())
                            newOwners = selectCurrentAliveOwners(aliveNodes, curOwners);
                        else if (curOwners.get(0).equals(leftNode))
                            newOwners = selectPrimaryTopologyOwnerFromIdealAssignment(
                                grpHolder.aff,
                                p,
                                top,
                                idealOwners,
                                waitRebalanceInfo
                            );
                        else if (!curOwners.get(0).equals(idealOwners.get(0)))
                            newOwners = latePrimaryAssignment(
                                grpHolder.aff,
                                p,
                                curOwners.get(0),
                                idealOwners,
                                waitRebalanceInfo
                            );

                        if (newOwners != null) {
                            newAssignment.set(p, newOwners);

                            List<Long> clusterNodesAsOrder = newOwners.stream()
                                .map(NODE_TO_ORDER::apply)
                                .collect(Collectors.toList());

                            cacheAffinityDiff.put(p, clusterNodesAsOrder);

                            processedPartitions.set(p);
                        }
                    }
                }

                Set<Integer> partitionsWithChangedPrimary = grpHolder.affinity().partitionPrimariesDifferentToIdeal(affTopVer);

                // We need to re-check partitions for further correct late affinity assignment
                // where primary node is not as in ideal assignment.
                for (int p : partitionsWithChangedPrimary) {
                    if (processedPartitions.get(p))
                        continue;

                    List<ClusterNode> curOwners = curAssignment.get(p);

                    if (curOwners.isEmpty())
                        continue;

                    List<ClusterNode> idealOwners = idealAssignment.assignment().get(p);

                    List<ClusterNode> newOwners = null;

                    if (idealOwners.isEmpty())
                        newOwners = selectCurrentAliveOwners(aliveNodes, curOwners);
                    else if (!aliveNodes.contains(curOwners.get(0)))
                        newOwners = selectPrimaryTopologyOwnerFromIdealAssignment(
                            grpHolder.aff,
                            p,
                            top,
                            idealOwners,
                            waitRebalanceInfo
                        );
                    else if (!curOwners.get(0).equals(idealOwners.get(0)))
                        // Current distribution was already not ideal. Preserve it for late affinity assignment.
                        newOwners = latePrimaryAssignment(
                            grpHolder.aff,
                            p,
                            curOwners.get(0),
                            idealOwners,
                            waitRebalanceInfo
                        );

                    if (newOwners != null) {
                        newAssignment.set(p, newOwners);

                        List<Long> clusterNodesAsOrder = newOwners.stream()
                            .map(NODE_TO_ORDER::apply)
                            .collect(Collectors.toList());

                        cacheAffinityDiff.put(p, clusterNodesAsOrder);
                    }
                }

                if (!cacheAffinityDiff.isEmpty())
                    diff.put(grpHolder.groupId(), cacheAffinityDiff);

                grpHolder.affinity().initialize(topVer, newAssignment);

                fut.timeBag().finishLocalStage("Affinity initialization (on server left) " +
                    "[grp=" + desc.cacheOrGroupName() + "]");
            }
        });

        synchronized (mux) {
            this.waitInfo = !waitRebalanceInfo.empty() ? waitRebalanceInfo : null;

            WaitRebalanceInfo info = this.waitInfo;

            if (log.isDebugEnabled()) {
                log.debug("Computed new affinity after node left [topVer=" + topVer +
                    ", waitGrps=" + (info != null ? groupNames(info.waitGrps.keySet()) : null) + ']');
            }
        }

        return CacheGroupAffinityMessage.createAffinityDiffMessages(diff);
    }

    /**
     * Selects current alive owners for some partition as affinity distribution.
     *
     * @param aliveNodes Alive cluster nodes.
     * @param curOwners  Current affinity owners for some partition.
     *
     * @return List of current alive affinity owners.
     *         {@code null} if affinity owners should be inherited from ideal assignment as is.
     */
    private @Nullable List<ClusterNode> selectCurrentAliveOwners(
        Set<ClusterNode> aliveNodes,
        List<ClusterNode> curOwners
    ) {
        List<ClusterNode> aliveCurOwners = curOwners.stream().filter(aliveNodes::contains).collect(Collectors.toList());

        return !aliveCurOwners.isEmpty() ? aliveCurOwners : null;
    }

    /**
     * Selects a node from ideal assignment that holds {@code OWNING} status for given partition as affinity primary.
     * Other nodes from ideal assignment are selected as backups.
     *
     * @param aff               Affinity assignment cache.
     * @param partition         Partition number.
     * @param topology          Partition topology for cache.
     * @param idealOwners       Ideal affinity distribution for given partition.
     * @param waitRebalanceInfo Wait rebalance info for late affinity assignment.

     * @return List of affinity owners where first node is primary and holds {@code OWNING} partition status.
     *         {@code null} if affinity owners should be inherited from ideal assignment as is.
     */
    private @Nullable List<ClusterNode> selectPrimaryTopologyOwnerFromIdealAssignment(
        GridAffinityAssignmentCache aff,
        int partition,
        GridDhtPartitionTopology topology,
        List<ClusterNode> idealOwners,
        WaitRebalanceInfo waitRebalanceInfo
    ) {
        ClusterNode newPrimary = idealOwners.get(0);

        if (topology.partitionState(newPrimary.id(), partition) != GridDhtPartitionState.OWNING) {
            for (ClusterNode node : idealOwners) {
                if (topology.partitionState(node.id(), partition) == GridDhtPartitionState.OWNING) {
                    newPrimary = node;

                    break;
                }
            }
        }

        // If primary by ideal assignment is already topology owner, no need to change affinity for that partition.
        if (newPrimary.equals(idealOwners.get(0)))
            return null;

        // In other case re-select primary with late affinity assignment.
        return latePrimaryAssignment(
            aff,
            partition,
            newPrimary,
            idealOwners,
            waitRebalanceInfo);
    }

    /**
     * Calculates affinity on coordinator for custom event types that require centralized assignment.
     *
     * @param fut Current exchange future.
     * @return Computed difference with ideal affinity.
     * @throws IgniteCheckedException If failed.
     */
    public Map<Integer, CacheGroupAffinityMessage> onCustomEventWithEnforcedAffinityReassignment(
        final GridDhtPartitionsExchangeFuture fut) throws IgniteCheckedException
    {
        assert DiscoveryCustomEvent.requiresCentralizedAffinityAssignment(fut.firstEvent());

        Map<Integer, CacheGroupAffinityMessage> result = onReassignmentEnforced(fut);

        return result;
    }

    /**
     * Calculates new affinity assignment on coordinator and creates affinity diff messages for other nodes.
     *
     * @param fut Current exchange future.
     * @return Computed difference with ideal affinity.
     * @throws IgniteCheckedException If failed.
     */
    public Map<Integer, CacheGroupAffinityMessage> onReassignmentEnforced(
        final GridDhtPartitionsExchangeFuture fut) throws IgniteCheckedException
    {
        final ExchangeDiscoveryEvents evts = fut.context().events();

        forAllRegisteredCacheGroups(new IgniteInClosureX<CacheGroupDescriptor>() {
            @Override public void applyx(CacheGroupDescriptor desc) throws IgniteCheckedException {
                AffinityTopologyVersion topVer = evts.topologyVersion();

                CacheGroupHolder grpHolder = getOrCreateGroupHolder(topVer, desc);

                // Already calculated.
                if (grpHolder.affinity().lastVersion().equals(topVer))
                    return;

                List<List<ClusterNode>> assign = grpHolder.affinity().calculate(topVer, evts, evts.discoveryCache()).assignment();

                if (!grpHolder.rebalanceEnabled || fut.cacheGroupAddedOnExchange(desc.groupId(), desc.receivedFrom()))
                    grpHolder.affinity().initialize(topVer, assign);

                fut.timeBag().finishLocalStage("Affinity initialization (enforced) " +
                    "[grp=" + desc.cacheOrGroupName() + "]");
            }
        });

        Map<Integer, Map<Integer, List<Long>>> diff = initAffinityBasedOnPartitionsAvailability(evts.topologyVersion(),
            fut,
            NODE_TO_ORDER,
            true);

        return CacheGroupAffinityMessage.createAffinityDiffMessages(diff);
    }

    /**
     * Called on exchange initiated by server node join.
     *
     * @param fut Exchange future.
     * @param crd Coordinator flag.
     * @throws IgniteCheckedException If failed.
     */
    public void onServerJoin(final GridDhtPartitionsExchangeFuture fut, boolean crd)
        throws IgniteCheckedException {
        assert !fut.firstEvent().eventNode().isClient();

        boolean locJoin = fut.firstEvent().eventNode().isLocal();

        WaitRebalanceInfo waitRebalanceInfo = null;

        if (locJoin) {
            forAllRegisteredCacheGroups(new IgniteInClosureX<CacheGroupDescriptor>() {
                @Override public void applyx(CacheGroupDescriptor desc) throws IgniteCheckedException {
                    AffinityTopologyVersion topVer = fut.initialVersion();

                    CacheGroupHolder grpHolder = getOrCreateGroupHolder(topVer, desc);

                    if (crd) {
                        calculateAndInit(fut.events(), grpHolder.affinity(), topVer);

                        cctx.exchange().exchangerUpdateHeartbeat();

                        fut.timeBag().finishLocalStage("First node affinity initialization (node join) " +
                            "[grp=" + desc.cacheOrGroupName() + "]");
                    }
                }
            });

            if (!crd) {
                fetchAffinityOnJoin(fut);

                fut.timeBag().finishLocalStage("Affinity fetch");
            }
        }
        else
            waitRebalanceInfo = initAffinityOnNodeJoin(fut, crd);

        this.waitInfo = waitRebalanceInfo != null && !waitRebalanceInfo.empty() ? waitRebalanceInfo : null;

        WaitRebalanceInfo info = this.waitInfo;

        if (crd) {
            if (log.isDebugEnabled()) {
                log.debug("Computed new affinity after node join [topVer=" + fut.initialVersion() +
                    ", waitGrps=" + (info != null ? groupNames(info.waitGrps.keySet()) : null) + ']');
            }
        }
    }

    /**
     * @param fut Exchange future
     * @param crd Coordinator flag.
     * @throws IgniteCheckedException If failed.
     */
    public void onBaselineTopologyChanged(final GridDhtPartitionsExchangeFuture fut, boolean crd) throws IgniteCheckedException {
        assert !fut.firstEvent().eventNode().isClient();

        WaitRebalanceInfo waitRebalanceInfo = initAffinityOnNodeJoin(fut, crd);

        this.waitInfo = waitRebalanceInfo != null && !waitRebalanceInfo.empty() ? waitRebalanceInfo : null;

        WaitRebalanceInfo info = this.waitInfo;

        if (crd) {
            if (log.isDebugEnabled()) {
                log.debug("Computed new affinity after node join [topVer=" + fut.initialVersion() +
                    ", waitGrps=" + (info != null ? groupNames(info.waitGrps.keySet()) : null) + ']');
            }
        }
    }

    /**
     * @param grpIds Cache group IDs.
     * @return Cache names.
     */
    private String groupNames(Collection<Integer> grpIds) {
        StringBuilder names = new StringBuilder();

        for (Integer grpId : grpIds) {
            String name = cachesRegistry.group(grpId).cacheOrGroupName();

            if (names.length() != 0)
                names.append(", ");

            names.append(name);
        }

        return names.toString();
    }

    /**
     * @param grpId Group ID.
     * @return Group name for debug purpose.
     */
    private String debugGroupName(int grpId) {
        CacheGroupDescriptor desc = cachesRegistry.group(grpId);

        if (desc != null)
            return desc.cacheOrGroupName();
        else
            return "Unknown group: " + grpId;
    }

    /**
     * @param evts Discovery events.
     * @param aff Affinity.
     * @param topVer Topology version.
     */
    private void calculateAndInit(ExchangeDiscoveryEvents evts,
        GridAffinityAssignmentCache aff,
        AffinityTopologyVersion topVer)
    {
        List<List<ClusterNode>> assignment = aff.calculate(topVer, evts, evts.discoveryCache()).assignment();

        aff.initialize(topVer, assignment);
    }

    /**
     * @param fut Exchange future.
     * @throws IgniteCheckedException If failed.
     */
    private void fetchAffinityOnJoin(GridDhtPartitionsExchangeFuture fut) throws IgniteCheckedException {
        AffinityTopologyVersion topVer = fut.initialVersion();

        List<GridDhtAssignmentFetchFuture> fetchFuts = Collections.synchronizedList(new ArrayList<>());

        forAllRegisteredCacheGroups(new IgniteInClosureX<CacheGroupDescriptor>() {
            @Override public void applyx(CacheGroupDescriptor desc) throws IgniteCheckedException {
                if (cctx.localNode().isClient() && cctx.cache().cacheGroup(desc.groupId()) == null)
                    return; // Skip non-started caches on client nodes.

                CacheGroupHolder holder = getOrCreateGroupHolder(topVer, desc);

                if (fut.cacheGroupAddedOnExchange(desc.groupId(), desc.receivedFrom())) {
                    // In case if merge is allowed do not calculate affinity since it can change on exchange end.
                    if (!fut.context().mergeExchanges())
                        calculateAndInit(fut.events(), holder.affinity(), topVer);
                }
                else {
                    if (fut.context().fetchAffinityOnJoin()) {
                        GridDhtAssignmentFetchFuture fetchFut = new GridDhtAssignmentFetchFuture(cctx,
                            desc.groupId(),
                            topVer,
                            fut.events().discoveryCache());

                        fetchFut.init(false);

                        fetchFuts.add(fetchFut);
                    }
                    else {
                        if (!fut.events().discoveryCache().serverNodes().isEmpty())
                            fut.context().addGroupAffinityRequestOnJoin(desc.groupId());
                        else
                            calculateAndInit(fut.events(), holder.affinity(), topVer);
                    }
                }

                cctx.exchange().exchangerUpdateHeartbeat();
            }
        });

        for (int i = 0; i < fetchFuts.size(); i++) {
            GridDhtAssignmentFetchFuture fetchFut = fetchFuts.get(i);

            int grpId = fetchFut.groupId();

            fetchAffinity(topVer,
                fut.events(),
                fut.events().discoveryCache(),
                groupAffinity(grpId),
                fetchFut);

            cctx.exchange().exchangerUpdateHeartbeat();
        }
    }

    /**
     * @param topVer Topology version.
     * @param events Discovery events.
     * @param discoCache Discovery data cache.
     * @param affCache Affinity.
     * @param fetchFut Affinity fetch future.
     * @throws IgniteCheckedException If failed.
     * @return Affinity assignment response.
     */
    private GridDhtAffinityAssignmentResponse fetchAffinity(
        AffinityTopologyVersion topVer,
        @Nullable ExchangeDiscoveryEvents events,
        DiscoCache discoCache,
        GridAffinityAssignmentCache affCache,
        GridDhtAssignmentFetchFuture fetchFut
    ) throws IgniteCheckedException {
        assert affCache != null;

        GridDhtAffinityAssignmentResponse res = fetchFut.get();

        if (res == null) {
            List<List<ClusterNode>> aff = affCache.calculate(topVer, events, discoCache).assignment();

            affCache.initialize(topVer, aff);
        }
        else {
            List<List<ClusterNode>> idealAff = res.idealAffinityAssignment(discoCache);

            if (idealAff != null)
                affCache.idealAssignment(topVer, idealAff);
            else {
                assert !affCache.centralizedAffinityFunction();

                affCache.calculate(topVer, events, discoCache);
            }

            List<List<ClusterNode>> aff = res.affinityAssignment(discoCache);

            assert aff != null : res;

            affCache.initialize(topVer, aff);
        }

        return res;
    }

    /**
     * Called on exchange initiated by server node leave or custom event with centralized affinity assignment.
     *
     * @param fut Exchange future.
     * @param crd Coordinator flag.
     * @throws IgniteCheckedException If failed.
     * @return {@code True} if affinity should be assigned by coordinator.
     */
    public boolean onCentralizedAffinityChange(final GridDhtPartitionsExchangeFuture fut, boolean crd) throws IgniteCheckedException {
        assert (fut.events().hasServerLeft() && !fut.firstEvent().eventNode().isClient()) ||
            DiscoveryCustomEvent.requiresCentralizedAffinityAssignment(fut.firstEvent()) : fut.firstEvent();

        forAllRegisteredCacheGroups(new IgniteInClosureX<CacheGroupDescriptor>() {
            @Override public void applyx(CacheGroupDescriptor desc) throws IgniteCheckedException {
                CacheGroupHolder cache = getOrCreateGroupHolder(fut.initialVersion(), desc);

                cache.aff.calculate(fut.initialVersion(), fut.events(), fut.events().discoveryCache());

                cctx.exchange().exchangerUpdateHeartbeat();

                fut.timeBag().finishLocalStage("Affinity centralized initialization (crd) " +
                    "[grp=" + desc.cacheOrGroupName() + ", crd=" + crd + "]");
            }
        });

        synchronized (mux) {
            this.waitInfo = null;
        }

        return true;
    }

    /**
     * @param fut Exchange future.
     * @param newAff {@code True} if there are no older nodes with affinity info available.
     * @throws IgniteCheckedException If failed.
     * @return Future completed when caches initialization is done.
     */
    public IgniteInternalFuture<?> initCoordinatorCaches(
        final GridDhtPartitionsExchangeFuture fut,
        final boolean newAff
    ) throws IgniteCheckedException {
        boolean locJoin = fut.firstEvent().eventNode().isLocal();

        if (!locJoin)
            return null;

        final List<IgniteInternalFuture<AffinityTopologyVersion>> futs = Collections.synchronizedList(new ArrayList<>());

        final AffinityTopologyVersion topVer = fut.initialVersion();

        forAllRegisteredCacheGroups(new IgniteInClosureX<CacheGroupDescriptor>() {
            @Override public void applyx(CacheGroupDescriptor desc) throws IgniteCheckedException {
                CacheGroupHolder grpHolder = getOrCreateGroupHolder(topVer, desc);

                if (grpHolder.affinity().idealAssignmentRaw() != null)
                    return;

                // Need initialize holders and affinity if this node became coordinator during this exchange.
                int grpId = desc.groupId();

                CacheGroupContext grp = cctx.cache().cacheGroup(grpId);

                if (grp == null) {
                    grpHolder = createHolder(cctx, desc, topVer, null);

                    final GridAffinityAssignmentCache aff = grpHolder.affinity();

                    if (newAff) {
                        if (!aff.lastVersion().equals(topVer))
                            calculateAndInit(fut.events(), aff, topVer);

                        grpHolder.topology(fut.context().events().discoveryCache()).beforeExchange(fut, true, false);
                    }
                    else {
                        List<GridDhtPartitionsExchangeFuture> exchFuts = cctx.exchange().exchangeFutures();

                        int idx = exchFuts.indexOf(fut);

                        assert idx >= 0 && idx < exchFuts.size() - 1 : "Invalid exchange futures state [cur=" + idx +
                            ", total=" + exchFuts.size() + ']';

                        GridDhtPartitionsExchangeFuture futureToFetchAffinity = null;

                        for (int i = idx + 1; i < exchFuts.size(); i++) {
                            GridDhtPartitionsExchangeFuture prev = exchFuts.get(i);

                            assert prev.isDone() && prev.topologyVersion().compareTo(topVer) < 0;

                            if (prev.isMerged())
                                continue;

                            futureToFetchAffinity = prev;

                            break;
                        }

                        if (futureToFetchAffinity == null)
                            throw new IgniteCheckedException("Failed to find completed exchange future to fetch affinity.");

                        if (log.isDebugEnabled()) {
                            log.debug("Need initialize affinity on coordinator [" +
                                "cacheGrp=" + desc.cacheOrGroupName() +
                                "prevAff=" + futureToFetchAffinity.topologyVersion() + ']');
                        }

                        GridDhtAssignmentFetchFuture fetchFut = new GridDhtAssignmentFetchFuture(
                                cctx,
                                desc.groupId(),
                                futureToFetchAffinity.topologyVersion(),
                                futureToFetchAffinity.events().discoveryCache()
                        );

                        fetchFut.init(false);

                        final GridFutureAdapter<AffinityTopologyVersion> affFut = new GridFutureAdapter<>();

                        final GridDhtPartitionsExchangeFuture futureToFetchAffinity0 = futureToFetchAffinity;

                        fetchFut.listen(new IgniteInClosureX<IgniteInternalFuture<GridDhtAffinityAssignmentResponse>>() {
                            @Override public void applyx(IgniteInternalFuture<GridDhtAffinityAssignmentResponse> fetchFut)
                                    throws IgniteCheckedException {
                                fetchAffinity(
                                        futureToFetchAffinity0.topologyVersion(),
                                        futureToFetchAffinity0.events(),
                                        futureToFetchAffinity0.events().discoveryCache(),
                                        aff,
                                        (GridDhtAssignmentFetchFuture)fetchFut
                                );

                                aff.calculate(topVer, fut.events(), fut.events().discoveryCache());

                                affFut.onDone(topVer);

                                cctx.exchange().exchangerUpdateHeartbeat();
                            }
                        });

                        futs.add(affFut);
                    }
                }
                else {
                    grpHolder = new CacheGroupAffNodeHolder(grp);

                    if (newAff) {
                        GridAffinityAssignmentCache aff = grpHolder.affinity();

                        if (!aff.lastVersion().equals(topVer))
                            calculateAndInit(fut.events(), aff, topVer);

                        grpHolder.topology(fut.context().events().discoveryCache()).beforeExchange(fut, true, false);
                    }
                }

                grpHolders.put(grpHolder.groupId(), grpHolder);

                cctx.exchange().exchangerUpdateHeartbeat();

                fut.timeBag().finishLocalStage("Coordinator affinity cache init " +
                    "[grp=" + desc.cacheOrGroupName() + "]");
            }
        });

        if (!futs.isEmpty()) {
            GridCompoundFuture<AffinityTopologyVersion, ?> affFut = new GridCompoundFuture<>();

            for (IgniteInternalFuture<AffinityTopologyVersion> f : futs)
                affFut.add(f);

            affFut.markInitialized();

            return affFut;
        }

        return null;
    }

    /**
     * @param topVer Topology version.
     * @param desc Cache descriptor.
     * @return Cache holder.
     * @throws IgniteCheckedException If failed.
     */
    private CacheGroupHolder getOrCreateGroupHolder(AffinityTopologyVersion topVer, CacheGroupDescriptor desc)
        throws IgniteCheckedException {
        CacheGroupHolder cacheGrp = grpHolders.get(desc.groupId());

        if (cacheGrp != null)
            return cacheGrp;

        return createGroupHolder(topVer, desc, cctx.cache().cacheGroup(desc.groupId()) != null);
    }

    /**
     * @param topVer Topology version.
     * @param desc Cache descriptor.
     * @param affNode Affinity node flag.
     * @return Cache holder.
     * @throws IgniteCheckedException If failed.
     */
    private CacheGroupHolder createGroupHolder(
        AffinityTopologyVersion topVer,
        CacheGroupDescriptor desc,
        boolean affNode
    ) throws IgniteCheckedException {
        assert topVer != null;
        assert desc != null;

        CacheGroupContext grp = cctx.cache().cacheGroup(desc.groupId());

        cctx.io().addCacheGroupHandler(desc.groupId(), GridDhtAffinityAssignmentResponse.class,
            this::processAffinityAssignmentResponse);

        assert (affNode && grp != null) || (!affNode && grp == null);

        CacheGroupHolder cacheGrp = affNode ?
            new CacheGroupAffNodeHolder(grp) :
            createHolder(cctx, desc, topVer, null);

        CacheGroupHolder old = grpHolders.put(desc.groupId(), cacheGrp);

        assert old == null : old;

        return cacheGrp;
    }

    /**
     * @param fut Current exchange future.
     * @param crd Coordinator flag.
     * @return Rabalance info.
     */
    @Nullable private WaitRebalanceInfo initAffinityOnNodeJoin(final GridDhtPartitionsExchangeFuture fut, boolean crd) {
        final ExchangeDiscoveryEvents evts = fut.context().events();

        final WaitRebalanceInfo waitRebalanceInfo = new WaitRebalanceInfo(evts.lastServerEventVersion());

        forAllRegisteredCacheGroups(new IgniteInClosureX<CacheGroupDescriptor>() {
            @Override public void applyx(CacheGroupDescriptor desc) throws IgniteCheckedException {
                if (cctx.localNode().isClient() && cctx.cache().cacheGroup(desc.groupId()) == null)
                    return;

                CacheGroupHolder cache = getOrCreateGroupHolder(evts.topologyVersion(), desc);

                // Already calculated.
                if (cache.affinity().lastVersion().equals(evts.topologyVersion()))
                    return;

                boolean latePrimary = cache.rebalanceEnabled;

                boolean grpAdded = evts.nodeJoined(desc.receivedFrom());

                initAffinityOnNodeJoin(evts,
                    grpAdded,
                    cache.affinity(),
                    crd ? waitRebalanceInfo : null,
                    latePrimary);

                if (crd && grpAdded) {
                    AffinityAssignment aff = cache.aff.cachedAffinity(cache.aff.lastVersion());

                    assert evts.topologyVersion().equals(aff.topologyVersion()) : "Unexpected version [" +
                        "grp=" + cache.aff.cacheOrGroupName() +
                        ", evts=" + evts.topologyVersion() +
                        ", aff=" + cache.aff.lastVersion() + ']';

                    Map<UUID, GridDhtPartitionMap> map = affinityFullMap(aff);

                    for (GridDhtPartitionMap map0 : map.values())
                        cache.topology(fut.context().events().discoveryCache()).update(fut.exchangeId(), map0, true);
                }

                cctx.exchange().exchangerUpdateHeartbeat();

                fut.timeBag().finishLocalStage("Affinity initialization (node join) " +
                    "[grp=" + desc.cacheOrGroupName() + ", crd=" + crd + "]");
            }
        });

        return waitRebalanceInfo;
    }

    /**
     * @param aff Affinity assignment.
     */
    private Map<UUID, GridDhtPartitionMap> affinityFullMap(AffinityAssignment aff) {
        Map<UUID, GridDhtPartitionMap> map = new HashMap<>();

        for (int p = 0; p < aff.assignment().size(); p++) {
            Collection<UUID> ids = aff.getIds(p);

            for (UUID nodeId : ids) {
                GridDhtPartitionMap partMap = map.get(nodeId);

                if (partMap == null) {
                    partMap = new GridDhtPartitionMap(nodeId,
                        1L,
                        aff.topologyVersion(),
                        new GridPartitionStateMap(),
                        false);

                    map.put(nodeId, partMap);
                }

                partMap.put(p, GridDhtPartitionState.OWNING);
            }
        }

        return map;
    }

    /**
     * @param evts Discovery events processed during exchange.
     * @param addedOnExchnage {@code True} if cache group was added during this exchange.
     * @param aff Affinity.
     * @param rebalanceInfo Rebalance information.
     * @param latePrimary If {@code true} delays primary assignment if it is not owner.
     */
    private void initAffinityOnNodeJoin(
        ExchangeDiscoveryEvents evts,
        boolean addedOnExchnage,
        GridAffinityAssignmentCache aff,
        WaitRebalanceInfo rebalanceInfo,
        boolean latePrimary
    ) {
        if (addedOnExchnage) {
            if (!aff.lastVersion().equals(evts.topologyVersion()))
                calculateAndInit(evts, aff, evts.topologyVersion());

            return;
        }

        AffinityTopologyVersion affTopVer = aff.lastVersion();

        assert affTopVer.topologyVersion() > 0 : "Affinity is not initialized [grp=" + aff.cacheOrGroupName() +
            ", topVer=" + affTopVer + ", node=" + cctx.localNodeId() + ']';

        assert aff.idealAssignmentRaw() != null : "Previous assignment is not available.";

        IdealAffinityAssignment idealAssignment = aff.calculate(evts.topologyVersion(), evts, evts.discoveryCache());
        List<List<ClusterNode>> curAssignment = aff.assignments(affTopVer);
        List<List<ClusterNode>> newAssignment = null;

        if (latePrimary) {
            BitSet processedPartitions = new BitSet(curAssignment.size());

            // Late affinity assignment to changed primaries.
            for (ClusterNode joinedNode : evts.joinedServerNodes()) {
                Set<Integer> primaries = idealAssignment.idealPrimaries(joinedNode);

                for (int p : primaries) {
                    List<ClusterNode> curNodes = curAssignment.get(p);

                    if (curNodes.isEmpty())
                        continue;

                    ClusterNode curPrimary = curNodes.get(0);

                    assert cctx.discovery().node(evts.topologyVersion(), curPrimary.id()) != null : curPrimary;

                    List<ClusterNode> idealNodes = idealAssignment.assignment().get(p);

                    List<ClusterNode> newNodes = latePrimaryAssignment(aff,
                        p,
                        curPrimary,
                        idealNodes,
                        rebalanceInfo);

                    if (newAssignment == null)
                        newAssignment = new ArrayList<>(idealAssignment.assignment());

                    newAssignment.set(p, newNodes);

                    processedPartitions.set(p);
                }
            }

            Set<Integer> partitionsWithChangedPrimary = aff.partitionPrimariesDifferentToIdeal(affTopVer);

            for (int p : partitionsWithChangedPrimary) {
                // Already processed above.
                if (processedPartitions.get(p))
                    continue;

                List<ClusterNode> curNodes = curAssignment.get(p);

                if (curNodes.isEmpty())
                    continue;

                List<ClusterNode> idealOwners = idealAssignment.assignment().get(p);

                if (!curNodes.get(0).equals(idealOwners.get(0))) {
                    List<ClusterNode> newNodes = latePrimaryAssignment(aff,
                        p,
                        curNodes.get(0),
                        idealOwners,
                        rebalanceInfo);

                    if (newAssignment == null)
                        newAssignment = new ArrayList<>(idealAssignment.assignment());

                    newAssignment.set(p, newNodes);
                }
            }
        }

        if (newAssignment == null)
            newAssignment = idealAssignment.assignment();

        aff.initialize(evts.topologyVersion(), newAssignment);
    }

    /**
     * @param aff Cache.
     * @param part Partition.
     * @param curPrimary Current primary.
     * @param newNodes New ideal assignment.
     * @param rebalance Rabalance information holder.
     * @return Assignment.
     */
    private List<ClusterNode> latePrimaryAssignment(
        GridAffinityAssignmentCache aff,
        int part,
        ClusterNode curPrimary,
        List<ClusterNode> newNodes,
        WaitRebalanceInfo rebalance) {
        assert curPrimary != null;
        assert !F.isEmpty(newNodes);
        assert !curPrimary.equals(newNodes.get(0));

        List<ClusterNode> nodes0 = new ArrayList<>(newNodes.size() + 1);

        nodes0.add(curPrimary);

        for (int i = 0; i < newNodes.size(); i++) {
            ClusterNode node = newNodes.get(i);

            if (!node.equals(curPrimary))
                nodes0.add(node);
        }

        if (rebalance != null)
            rebalance.add(aff.groupId(), part, newNodes.get(0).id(), newNodes);

        return nodes0;
    }

    /**
     * @param fut Exchange future.
     * @return Affinity assignment.
     * @throws IgniteCheckedException If failed.
     */
    public IgniteInternalFuture<Map<Integer, Map<Integer, List<UUID>>>> initAffinityOnNodeLeft(
        final GridDhtPartitionsExchangeFuture fut) throws IgniteCheckedException {
        assert !fut.context().mergeExchanges();

        IgniteInternalFuture<?> initFut = initCoordinatorCaches(fut, false);

        if (initFut != null && !initFut.isDone()) {
            final GridFutureAdapter<Map<Integer, Map<Integer, List<UUID>>>> resFut = new GridFutureAdapter<>();

            initFut.listen(new IgniteInClosure<IgniteInternalFuture<?>>() {
                @Override public void apply(IgniteInternalFuture<?> initFut) {
                    try {
                        resFut.onDone(initAffinityBasedOnPartitionsAvailability(fut.initialVersion(), fut, NODE_TO_ID, false));
                    }
                    catch (Exception e) {
                        resFut.onDone(e);
                    }
                }
            });

            return resFut;
        }
        else
            return new GridFinishedFuture<>(initAffinityBasedOnPartitionsAvailability(fut.initialVersion(), fut, NODE_TO_ID, false));
    }

    /**
     * Initializes current affinity assignment based on partitions availability.
     * Nodes that have most recent data will be considered affinity nodes.
     *
     * @param topVer Topology version.
     * @param fut Exchange future.
     * @param c Closure converting affinity diff.
     * @param initAff {@code True} if need initialize affinity.
     *
     * @return Affinity assignment for each of registered cache group.
     */
    private <T> Map<Integer, Map<Integer, List<T>>> initAffinityBasedOnPartitionsAvailability(
        final AffinityTopologyVersion topVer,
        final GridDhtPartitionsExchangeFuture fut,
        final IgniteClosure<ClusterNode, T> c,
        final boolean initAff
    ) {
        final boolean enforcedCentralizedAssignment =
            DiscoveryCustomEvent.requiresCentralizedAffinityAssignment(fut.firstEvent());

        final WaitRebalanceInfo waitRebalanceInfo = enforcedCentralizedAssignment ?
            new WaitRebalanceInfo(fut.exchangeId().topologyVersion()) :
            new WaitRebalanceInfo(fut.context().events().lastServerEventVersion());

        final Collection<ClusterNode> aliveNodes = fut.context().events().discoveryCache().serverNodes();

        final Map<Integer, Map<Integer, List<T>>> assignment = new ConcurrentHashMap<>();

        forAllRegisteredCacheGroups(new IgniteInClosureX<CacheGroupDescriptor>() {
            @Override public void applyx(CacheGroupDescriptor desc) throws IgniteCheckedException {
                CacheGroupHolder grpHolder = getOrCreateGroupHolder(topVer, desc);

                if (!grpHolder.rebalanceEnabled ||
                    (fut.cacheGroupAddedOnExchange(desc.groupId(), desc.receivedFrom()) && !enforcedCentralizedAssignment))
                    return;

                AffinityTopologyVersion affTopVer = grpHolder.affinity().lastVersion();

                assert (affTopVer.topologyVersion() > 0 && !affTopVer.equals(topVer)) || enforcedCentralizedAssignment :
                    "Invalid affinity version [last=" + affTopVer + ", futVer=" + topVer + ", grp=" + desc.cacheOrGroupName() + ']';

                List<List<ClusterNode>> curAssignment = grpHolder.affinity().assignments(affTopVer);
                List<List<ClusterNode>> newAssignment = grpHolder.affinity().idealAssignmentRaw();

                assert newAssignment != null;

                List<List<ClusterNode>> newAssignment0 = initAff ? new ArrayList<>(newAssignment) : null;

                GridDhtPartitionTopology top = grpHolder.topology(fut.context().events().discoveryCache());

                Map<Integer, List<T>> cacheAssignment = null;

                for (int p = 0; p < newAssignment.size(); p++) {
                    List<ClusterNode> newNodes = newAssignment.get(p);
                    List<ClusterNode> curNodes = curAssignment.get(p);

                    assert aliveNodes.containsAll(newNodes) : "Invalid new assignment [grp=" + grpHolder.aff.cacheOrGroupName() +
                        ", nodes=" + newNodes +
                        ", topVer=" + fut.context().events().discoveryCache().version() +
                        ", evts=" + fut.context().events().events() + "]";

                    ClusterNode curPrimary = !curNodes.isEmpty() ? curNodes.get(0) : null;
                    ClusterNode newPrimary = !newNodes.isEmpty() ? newNodes.get(0) : null;

                    List<ClusterNode> newNodes0 = null;

                    assert newPrimary == null || aliveNodes.contains(newPrimary) : "Invalid new primary [" +
                        "grp=" + desc.cacheOrGroupName() +
                        ", node=" + newPrimary +
                        ", topVer=" + topVer + ']';

                    List<ClusterNode> owners = top.owners(p, topVer);

                    // It is essential that curPrimary node has partition in OWNING state.
                    if (!owners.isEmpty() && !owners.contains(curPrimary))
                        curPrimary = owners.get(0);

                    // If new assignment is empty preserve current ownership for alive nodes.
                    if (curPrimary != null && newPrimary == null) {
                        newNodes0 = new ArrayList<>(curNodes.size());

                        for (ClusterNode node : curNodes) {
                            if (aliveNodes.contains(node))
                                newNodes0.add(node);
                        }
                    }
                    else if (curPrimary != null && !curPrimary.equals(newPrimary)) {
                        GridDhtPartitionState state = top.partitionState(newPrimary.id(), p);

                        if (aliveNodes.contains(curPrimary)) {
                            if (state != GridDhtPartitionState.OWNING) {
                                newNodes0 = latePrimaryAssignment(grpHolder.affinity(),
                                    p,
                                    curPrimary,
                                    newNodes,
                                    waitRebalanceInfo);
                            }
                        }
                        else {
                            if (state != GridDhtPartitionState.OWNING) {
                                for (int i = 1; i < curNodes.size(); i++) {
                                    ClusterNode curNode = curNodes.get(i);

                                    if (top.partitionState(curNode.id(), p) == GridDhtPartitionState.OWNING &&
                                        aliveNodes.contains(curNode)) {
                                        newNodes0 = latePrimaryAssignment(grpHolder.affinity(),
                                            p,
                                            curNode,
                                            newNodes,
                                            waitRebalanceInfo);

                                        break;
                                    }
                                }

                                if (newNodes0 == null) {
                                    for (ClusterNode owner : owners) {
                                        if (aliveNodes.contains(owner)) {
                                            newNodes0 = latePrimaryAssignment(grpHolder.affinity(),
                                                p,
                                                owner,
                                                newNodes,
                                                waitRebalanceInfo);

                                            break;
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (newNodes0 != null) {
                        assert aliveNodes.containsAll(newNodes0) : "Invalid late assignment [grp=" + grpHolder.aff.cacheOrGroupName() +
                            ", nodes=" + newNodes +
                            ", topVer=" + fut.context().events().discoveryCache().version() +
                            ", evts=" + fut.context().events().events() + "]";

                        if (newAssignment0 != null)
                            newAssignment0.set(p, newNodes0);

                        if (cacheAssignment == null)
                            cacheAssignment = new HashMap<>();

                        List<T> n = new ArrayList<>(newNodes0.size());

                        for (int i = 0; i < newNodes0.size(); i++)
                            n.add(c.apply(newNodes0.get(i)));

                        cacheAssignment.put(p, n);
                    }
                }

                if (cacheAssignment != null)
                    assignment.put(grpHolder.groupId(), cacheAssignment);

                if (initAff)
                    grpHolder.affinity().initialize(topVer, newAssignment0);

                fut.timeBag().finishLocalStage("Affinity recalculation (partitions availability) " +
                    "[grp=" + desc.cacheOrGroupName() + "]");
            }
        });

        synchronized (mux) {
            this.waitInfo = !waitRebalanceInfo.empty() ? waitRebalanceInfo : null;

            WaitRebalanceInfo info = this.waitInfo;

            if (log.isDebugEnabled()) {
                log.debug("Computed new affinity after node left [topVer=" + topVer +
                    ", waitGrps=" + (info != null ? groupNames(info.waitGrps.keySet()) : null) + ']');
            }
        }

        return assignment;
    }

    /**
     * @return All registered cache groups.
     */
    public Map<Integer, CacheGroupDescriptor> cacheGroups() {
        return cachesRegistry.allGroups();
    }

    /**
     * @return All registered cache groups.
     */
    public Map<Integer, DynamicCacheDescriptor> caches() {
        return cachesRegistry.allCaches();
    }

    /**
     * @param grpId Cache group ID
     * @return Cache affinity cache.
     */
    @Nullable public GridAffinityAssignmentCache groupAffinity(int grpId) {
        CacheGroupHolder grpHolder = grpHolders.get(grpId);

        return grpHolder != null ? grpHolder.affinity() : null;
    }

    /**
     *
     */
    public void dumpDebugInfo() {
        if (!pendingAssignmentFetchFuts.isEmpty()) {
            U.warn(log, "Pending assignment fetch futures:");

            for (GridDhtAssignmentFetchFuture fut : pendingAssignmentFetchFuts.values())
                U.warn(log, ">>> " + fut);
        }
    }

    /**
     * @param nodes Nodes.
     * @return IDs.
     */
    private static List<UUID> toIds0(List<ClusterNode> nodes) {
        List<UUID> partIds = new ArrayList<>(nodes.size());

        for (int i = 0; i < nodes.size(); i++)
            partIds.add(nodes.get(i).id());

        return partIds;
    }

    /**
     * @param topVer Topology version.
     * @param ids IDs.
     * @return Nodes.
     */
    private List<ClusterNode> toNodes(AffinityTopologyVersion topVer, List<UUID> ids) {
        List<ClusterNode> nodes = new ArrayList<>(ids.size());

        for (int i = 0; i < ids.size(); i++) {
            UUID id = ids.get(i);

            ClusterNode node = cctx.discovery().node(topVer, id);

            assert node != null : "Failed to get node [id=" + id +
                ", topVer=" + topVer +
                ", locNode=" + cctx.localNode() +
                ", allNodes=" + cctx.discovery().nodes(topVer) + ']';

            nodes.add(node);
        }

        return nodes;
    }

    /**
     *
     */
    abstract class CacheGroupHolder {
        /** */
        private final GridAffinityAssignmentCache aff;

        /** */
        private final boolean rebalanceEnabled;

        /**
         * @param rebalanceEnabled Cache rebalance flag.
         * @param aff Affinity cache.
         * @param initAff Existing affinity cache.
         */
        CacheGroupHolder(boolean rebalanceEnabled,
            GridAffinityAssignmentCache aff,
            @Nullable GridAffinityAssignmentCache initAff) {
            this.aff = aff;

            if (initAff != null)
                aff.init(initAff);

            this.rebalanceEnabled = rebalanceEnabled;
        }

        /**
         * @return Client holder flag.
         */
        abstract boolean nonAffNode();

        /**
         * @return Group ID.
         */
        int groupId() {
            return aff.groupId();
        }

        /**
         * @return Partitions number.
         */
        int partitions() {
            return aff.partitions();
        }

        /**
         * @param discoCache Discovery data cache.
         * @return Cache topology.
         */
        abstract GridDhtPartitionTopology topology(DiscoCache discoCache);

        /**
         * @return Affinity.
         */
        GridAffinityAssignmentCache affinity() {
            return aff;
        }
    }

    /**
     * Created cache is started on coordinator.
     */
    private class CacheGroupAffNodeHolder extends CacheGroupHolder {
        /** */
        private final CacheGroupContext grp;

        /**
         * @param grp Cache group.
         */
        CacheGroupAffNodeHolder(CacheGroupContext grp) {
            this(grp, null);
        }

        /**
         * @param grp Cache group.
         * @param initAff Current affinity.
         */
        CacheGroupAffNodeHolder(CacheGroupContext grp, @Nullable GridAffinityAssignmentCache initAff) {
            super(grp.rebalanceEnabled(), grp.affinity(), initAff);

            assert !grp.isLocal() : grp;

            this.grp = grp;
        }

        /** {@inheritDoc} */
        @Override public boolean nonAffNode() {
            return false;
        }

        /** {@inheritDoc} */
        @Override public GridDhtPartitionTopology topology(DiscoCache discoCache) {
            return grp.topology();
        }
    }

    /**
     * Created if cache is not started on coordinator.
     */
    private class CacheGroupNoAffOrFiltredHolder extends CacheGroupHolder {
        /** */
        private final GridCacheSharedContext cctx;

        /**
         * @param rebalanceEnabled Rebalance flag.
         * @param cctx Context.
         * @param aff Affinity.
         * @param initAff Current affinity.
         */
        CacheGroupNoAffOrFiltredHolder(
            boolean rebalanceEnabled,
            GridCacheSharedContext cctx,
            GridAffinityAssignmentCache aff,
            @Nullable GridAffinityAssignmentCache initAff
        ) {
            super(rebalanceEnabled, aff, initAff);

            this.cctx = cctx;
        }

        /**
         * @param cctx Context.
         * @param grpDesc Cache group descriptor.
         * @param topVer Current exchange version.
         * @return Cache holder.
         * @throws IgniteCheckedException If failed.
         */
        CacheGroupNoAffOrFiltredHolder create(
            GridCacheSharedContext cctx,
            CacheGroupDescriptor grpDesc,
            AffinityTopologyVersion topVer
            ) throws IgniteCheckedException {
            return create(cctx, grpDesc, topVer, null);
        }

        /**
         * @param cctx Context.
         * @param grpDesc Cache group descriptor.
         * @param topVer Current exchange version.
         * @param initAff Current affinity.
         * @return Cache holder.
         * @throws IgniteCheckedException If failed.
         */
        CacheGroupNoAffOrFiltredHolder create(
            GridCacheSharedContext cctx,
            CacheGroupDescriptor grpDesc,
            AffinityTopologyVersion topVer,
            @Nullable GridAffinityAssignmentCache initAff
        ) throws IgniteCheckedException {
            assert grpDesc != null;
            assert !cctx.kernalContext().clientNode() || !CU.affinityNode(cctx.localNode(), grpDesc.config().getNodeFilter());

            CacheConfiguration<?, ?> ccfg = grpDesc.config();

            assert ccfg != null : grpDesc;
            assert ccfg.getCacheMode() != LOCAL : ccfg.getName();

            assert !cctx.discovery().cacheGroupAffinityNodes(grpDesc.groupId(),
                topVer).contains(cctx.localNode()) : grpDesc.cacheOrGroupName();

            AffinityFunction affFunc = cctx.cache().clone(ccfg.getAffinity());

            cctx.kernalContext().resource().injectGeneric(affFunc);
            cctx.kernalContext().resource().injectCacheName(affFunc, ccfg.getName());

            U.startLifecycleAware(F.asList(affFunc));

            GridAffinityAssignmentCache aff = new GridAffinityAssignmentCache(cctx.kernalContext(),
                grpDesc.cacheOrGroupName(),
                grpDesc.groupId(),
                affFunc,
                ccfg.getNodeFilter(),
                ccfg.getBackups(),
                ccfg.getCacheMode() == LOCAL
            );

            return new CacheGroupNoAffOrFiltredHolder(ccfg.getRebalanceMode() != NONE, cctx, aff, initAff);
        }

        /** {@inheritDoc} */
        @Override public boolean nonAffNode() {
            return true;
        }

        /** {@inheritDoc} */
        @Override public GridDhtPartitionTopology topology(DiscoCache discoCache) {
            return cctx.exchange().clientTopology(groupId(), discoCache);
        }
    }

    private CacheGroupNoAffOrFiltredHolder createHolder(
        GridCacheSharedContext cctx,
        CacheGroupDescriptor grpDesc,
        AffinityTopologyVersion topVer,
        @Nullable GridAffinityAssignmentCache initAff
    ) throws IgniteCheckedException {
        assert grpDesc != null;
        assert !cctx.kernalContext().clientNode() || !CU.affinityNode(cctx.localNode(), grpDesc.config().getNodeFilter());

        CacheConfiguration<?, ?> ccfg = grpDesc.config();

        assert ccfg != null : grpDesc;
        assert ccfg.getCacheMode() != LOCAL : ccfg.getName();

        assert !cctx.discovery().cacheGroupAffinityNodes(grpDesc.groupId(),
            topVer).contains(cctx.localNode()) : grpDesc.cacheOrGroupName();

        AffinityFunction affFunc = cctx.cache().clone(ccfg.getAffinity());

        cctx.kernalContext().resource().injectGeneric(affFunc);
        cctx.kernalContext().resource().injectCacheName(affFunc, ccfg.getName());

        U.startLifecycleAware(F.asList(affFunc));

        GridAffinityAssignmentCache aff = new GridAffinityAssignmentCache(cctx.kernalContext(),
            grpDesc.cacheOrGroupName(),
            grpDesc.groupId(),
            affFunc,
            ccfg.getNodeFilter(),
            ccfg.getBackups(),
            ccfg.getCacheMode() == LOCAL
        );

        return new CacheGroupNoAffOrFiltredHolder(ccfg.getRebalanceMode() != NONE, cctx, aff, initAff);
    }

    /**
     *
     */
    class WaitRebalanceInfo {
        /** */
        private final AffinityTopologyVersion topVer;

        /** */
        private final Map<Integer, Map<Integer, UUID>> waitGrps = new ConcurrentHashMap<>();

        /** */
        private final Map<Integer, Map<Integer, List<ClusterNode>>> assignments = new ConcurrentHashMap<>();

        /** */
        private final Map<Integer, IgniteUuid> deploymentIds = new ConcurrentHashMap<>();

        /**
         * @param topVer Topology version.
         */
        WaitRebalanceInfo(AffinityTopologyVersion topVer) {
            this.topVer = topVer;
        }

        /**
         * @return {@code True} if there are partitions waiting for rebalancing.
         */
        boolean empty() {
            boolean isEmpty = waitGrps.isEmpty();

            if (!isEmpty) {
                assert waitGrps.size() == assignments.size();

                return false;
            }

            return isEmpty;
        }

        /**
         * @param grpId Group ID.
         * @param part Partition.
         * @param waitNode Node rebalancing data.
         * @param assignment New assignment.
         */
        void add(Integer grpId, Integer part, UUID waitNode, List<ClusterNode> assignment) {
            assert !F.isEmpty(assignment) : assignment;

            deploymentIds.putIfAbsent(grpId, cachesRegistry.group(grpId).deploymentId());

            waitGrps.computeIfAbsent(grpId, k -> new HashMap<>()).put(part, waitNode);

            assignments.computeIfAbsent(grpId, k -> new HashMap<>()).put(part, assignment);
        }

        /** {@inheritDoc} */
        @Override public String toString() {
            return "WaitRebalanceInfo [topVer=" + topVer +
                ", grps=" + (waitGrps != null ? waitGrps.keySet() : null) + ']';
        }
    }
}
