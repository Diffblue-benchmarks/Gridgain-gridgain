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
package org.apache.ignite.internal.processors.cache.verify;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import org.apache.ignite.IgniteCheckedException;
import org.apache.ignite.IgniteException;
import org.apache.ignite.IgniteInterruptedException;
import org.apache.ignite.IgniteLogger;
import org.apache.ignite.cluster.ClusterNode;
import org.apache.ignite.compute.ComputeJob;
import org.apache.ignite.compute.ComputeJobAdapter;
import org.apache.ignite.compute.ComputeJobResult;
import org.apache.ignite.compute.ComputeJobResultPolicy;
import org.apache.ignite.compute.ComputeTaskAdapter;
import org.apache.ignite.configuration.CacheConfiguration;
import org.apache.ignite.configuration.DataStorageConfiguration;
import org.apache.ignite.internal.IgniteEx;
import org.apache.ignite.internal.processors.cache.CacheGroupContext;
import org.apache.ignite.internal.processors.cache.DynamicCacheDescriptor;
import org.apache.ignite.internal.processors.cache.GridCacheUtils;
import org.apache.ignite.internal.processors.cache.GridCacheContext;
import org.apache.ignite.internal.processors.cache.distributed.dht.topology.GridDhtLocalPartition;
import org.apache.ignite.internal.processors.cache.distributed.dht.topology.GridDhtPartitionState;
import org.apache.ignite.internal.processors.cache.persistence.CacheDataRow;
import org.apache.ignite.internal.processors.cache.persistence.DbCheckpointListener;
import org.apache.ignite.internal.processors.cache.persistence.GridCacheDatabaseSharedManager;
import org.apache.ignite.internal.processors.cache.persistence.file.FilePageStore;
import org.apache.ignite.internal.processors.cache.persistence.file.FilePageStoreManager;
import org.apache.ignite.internal.processors.task.GridInternal;
import org.apache.ignite.internal.util.lang.GridIterator;
import org.apache.ignite.internal.util.typedef.F;
import org.apache.ignite.internal.util.typedef.internal.SB;
import org.apache.ignite.internal.util.typedef.internal.U;
import org.apache.ignite.internal.visor.verify.VisorIdleVerifyDumpTaskArg;
import org.apache.ignite.internal.visor.verify.VisorIdleVerifyTaskArg;
import org.apache.ignite.lang.IgniteInClosure;
import org.apache.ignite.lang.IgniteProductVersion;
import org.apache.ignite.resources.IgniteInstanceResource;
import org.apache.ignite.resources.LoggerResource;
import org.jetbrains.annotations.Nullable;

import static org.apache.ignite.cache.CacheMode.LOCAL;
import static org.apache.ignite.internal.pagemem.PageIdAllocator.FLAG_DATA;

/**
 * Task for comparing update counters and checksums between primary and backup partitions of specified caches.
 * <br>
 * Argument: Set of cache names, 'null' will trigger verification for all caches.
 * <br>
 * Result: {@link IdleVerifyResultV2} with conflict partitions.
 * <br>
 * Works properly only on idle cluster - there may be false positive conflict reports if data in cluster is being
 * concurrently updated.
 */
@GridInternal
public class VerifyBackupPartitionsTaskV2 extends ComputeTaskAdapter<VisorIdleVerifyTaskArg, IdleVerifyResultV2> {
    /** First version of Ignite that is capable of executing Idle Verify V2. */
    public static final IgniteProductVersion V2_SINCE_VER = IgniteProductVersion.fromString("2.5.3");

    /** Injected logger. */
    @LoggerResource
    private IgniteLogger log;

    /** */
    private static final long serialVersionUID = 0L;

    /** {@inheritDoc} */
    @Nullable @Override public Map<? extends ComputeJob, ClusterNode> map(
        List<ClusterNode> subgrid,
        VisorIdleVerifyTaskArg arg
    ) throws IgniteException {
        Map<ComputeJob, ClusterNode> jobs = new HashMap<>();

        for (ClusterNode node : subgrid)
            jobs.put(new VerifyBackupPartitionsJobV2(arg), node);

        return jobs;
    }

    /** {@inheritDoc} */
    @Nullable @Override public IdleVerifyResultV2 reduce(List<ComputeJobResult> results) throws IgniteException {
        Map<PartitionKeyV2, List<PartitionHashRecordV2>> clusterHashes = new HashMap<>();

        Map<ClusterNode, Exception> exceptions = new HashMap<>();

        reduceResults(results, clusterHashes, exceptions);

        if (results.size() != exceptions.size())
            return checkConflicts(clusterHashes, exceptions);
        else
            return new IdleVerifyResultV2(new HashMap<>(), new HashMap<>(), new HashMap<>(), exceptions);
    }

    /** {@inheritDoc} */
    @Override public ComputeJobResultPolicy result(
        ComputeJobResult res,
        List<ComputeJobResult> rcvd
    ) throws IgniteException {
        try {
            ComputeJobResultPolicy superRes = super.result(res, rcvd);

            // Deny failover.
            if (superRes == ComputeJobResultPolicy.FAILOVER) {
                superRes = ComputeJobResultPolicy.WAIT;

                if (log != null) {
                    log.warning("VerifyBackupPartitionsJobV2 failed on node " +
                        "[consistentId=" + res.getNode().consistentId() + "]", res.getException());
                }
            }

            return superRes;
        }
        catch (IgniteException e) {
            return ComputeJobResultPolicy.WAIT;
        }
    }

    /** */
    private IdleVerifyResultV2 checkConflicts(
        Map<PartitionKeyV2, List<PartitionHashRecordV2>> clusterHashes,
        Map<ClusterNode, Exception> exceptions
    ) {
        Map<PartitionKeyV2, List<PartitionHashRecordV2>> hashConflicts = new HashMap<>();

        Map<PartitionKeyV2, List<PartitionHashRecordV2>> updateCntrConflicts = new HashMap<>();

        Map<PartitionKeyV2, List<PartitionHashRecordV2>> movingParts = new HashMap<>();

        for (Map.Entry<PartitionKeyV2, List<PartitionHashRecordV2>> e : clusterHashes.entrySet()) {
            Integer partHash = null;
            Long updateCntr = null;

            for (PartitionHashRecordV2 record : e.getValue()) {
                if (record.size() == PartitionHashRecordV2.MOVING_PARTITION_SIZE) {
                    List<PartitionHashRecordV2> records = movingParts.computeIfAbsent(
                        e.getKey(), k -> new ArrayList<>());

                    records.add(record);

                    continue;
                }

                if (partHash == null) {
                    partHash = record.partitionHash();

                    updateCntr = record.updateCounter();
                }
                else {
                    if (record.updateCounter() != updateCntr)
                        updateCntrConflicts.putIfAbsent(e.getKey(), e.getValue());

                    if (record.partitionHash() != partHash)
                        hashConflicts.putIfAbsent(e.getKey(), e.getValue());
                }
            }
        }

        return new IdleVerifyResultV2(updateCntrConflicts, hashConflicts, movingParts, exceptions);
    }

    /** */
    private void reduceResults(
        List<ComputeJobResult> results,
        Map<PartitionKeyV2, List<PartitionHashRecordV2>> clusterHashes,
        Map<ClusterNode, Exception> exceptions
    ) {
        for (ComputeJobResult res : results) {
            if (res.getException() != null) {
                exceptions.put(res.getNode(), res.getException());

                continue;
            }

            Map<PartitionKeyV2, PartitionHashRecordV2> nodeHashes = res.getData();

            for (Map.Entry<PartitionKeyV2, PartitionHashRecordV2> e : nodeHashes.entrySet()) {
                List<PartitionHashRecordV2> records = clusterHashes.computeIfAbsent(e.getKey(), k -> new ArrayList<>());

                records.add(e.getValue());
            }
        }
    }

    /**
     * Job that collects update counters and hashes of local partitions.
     */
    private static class VerifyBackupPartitionsJobV2 extends ComputeJobAdapter {
        /** */
        private static final long serialVersionUID = 0L;

        /** Ignite instance. */
        @IgniteInstanceResource
        private IgniteEx ignite;

        /** Injected logger. */
        @LoggerResource
        private IgniteLogger log;

        /** Idle verify arguments. */
        private VisorIdleVerifyTaskArg arg;

        /** Counter of processed partitions. */
        private final AtomicInteger completionCntr = new AtomicInteger(0);

        /**
         * @param arg Argument.
         */
        public VerifyBackupPartitionsJobV2(VisorIdleVerifyTaskArg arg) {
            this.arg = arg;
        }

        /** {@inheritDoc} */
        @Override public Map<PartitionKeyV2, PartitionHashRecordV2> execute() throws IgniteException {
            Set<Integer> grpIds = getGroupIds();

            completionCntr.set(0);

            AtomicBoolean cpFlag = new AtomicBoolean();

            GridCacheDatabaseSharedManager db = null;

            DbCheckpointListener lsnr = null;

            if (arg.isCheckCrc() &&
                ignite.context().cache().context().database() instanceof GridCacheDatabaseSharedManager) {
                db = (GridCacheDatabaseSharedManager)ignite.context().cache().context().database();

                lsnr = new DbCheckpointListener() {
                    @Override public void onMarkCheckpointBegin(Context ctx) {
                        /* No-op. */
                    }

                    @Override public void onCheckpointBegin(Context ctx) {
                        if (ctx.hasUserPages())
                            cpFlag.set(true);
                    }

                    @Override public void beforeCheckpointBegin(Context ctx) throws IgniteCheckedException {
                        /* No-op. */
                    }
                };

                db.addCheckpointListener(lsnr);
            }

            try {
                if (arg.isCheckCrc() && IdleVerifyUtility.isCheckpointNow(db))
                    throw new GridNotIdleException(IdleVerifyUtility.CLUSTER_NOT_IDLE_MSG);

                List<Future<Map<PartitionKeyV2, PartitionHashRecordV2>>> partHashCalcFuts =
                    calcPartitionHashAsync(grpIds, cpFlag);

                Map<PartitionKeyV2, PartitionHashRecordV2> res = new HashMap<>();

                List<IgniteException> exceptions = new ArrayList<>();

                long lastProgressLogTs = U.currentTimeMillis();

                for (int i = 0; i < partHashCalcFuts.size(); ) {
                    Future<Map<PartitionKeyV2, PartitionHashRecordV2>> fut = partHashCalcFuts.get(i);

                    try {
                        Map<PartitionKeyV2, PartitionHashRecordV2> partHash = fut.get(100, TimeUnit.MILLISECONDS);

                        res.putAll(partHash);

                        i++;
                    }
                    catch (InterruptedException | ExecutionException e) {
                        if (e.getCause() instanceof IgniteException && !(e.getCause() instanceof GridNotIdleException)) {
                            exceptions.add((IgniteException)e.getCause());

                            i++;

                            continue;
                        }

                        for (int j = i + 1; j < partHashCalcFuts.size(); j++)
                            partHashCalcFuts.get(j).cancel(false);

                        if (e instanceof InterruptedException)
                            throw new IgniteInterruptedException((InterruptedException)e);
                        else
                            throw new IgniteException(e.getCause());
                    }
                    catch (TimeoutException ignored) {
                        if (U.currentTimeMillis() - lastProgressLogTs > 3 * 60 * 1000L) {
                            lastProgressLogTs = U.currentTimeMillis();

                            log.warning("idle_verify is still running, processed " + completionCntr.get() + " of " +
                                partHashCalcFuts.size() + " local partitions");
                        }
                    }
                }

                if (!F.isEmpty(exceptions))
                    throw new IdleVerifyException(exceptions);

                return res;
            }
            finally {
                if (db != null && lsnr != null)
                    db.removeCheckpointListener(lsnr);
            }
        }

        /**
         * Class that processes cache filtering chain.
         */
        private class CachesFiltering {
            /**
             * Initially all cache descriptors are included.
             */
            private final Set<CacheGroupContext> filteredCacheGroups;

            /** */
            public CachesFiltering(Collection<CacheGroupContext> cacheGroups) {
                filteredCacheGroups = new HashSet<>(cacheGroups);
            }

            /**
             * Applies filtering closure.
             *
             * @param closure filter
             * @return this
             */
            public CachesFiltering filter(
                    IgniteInClosure<Set<CacheGroupContext>> closure) {
                closure.apply(filteredCacheGroups);

                return this;
            }

            /**
             * Returns result set of cache ids.
             *
             * @return set of filtered cache ids.
             */
            public Set<Integer> result() {
                Set<Integer> res = new HashSet<>();

                for (CacheGroupContext cacheGrp : filteredCacheGroups)
                    res.add(cacheGrp.groupId());

                return res;
            }
        }

        /** */
        private List<Future<Map<PartitionKeyV2, PartitionHashRecordV2>>> calcPartitionHashAsync(
            Set<Integer> grpIds,
            AtomicBoolean cpFlag
        ) {
            List<Future<Map<PartitionKeyV2, PartitionHashRecordV2>>> partHashCalcFutures = new ArrayList<>();

            for (Integer grpId : grpIds) {
                CacheGroupContext grpCtx = ignite.context().cache().cacheGroup(grpId);

                if (grpCtx == null)
                    continue;

                List<GridDhtLocalPartition> parts = grpCtx.topology().localPartitions();

                for (GridDhtLocalPartition part : parts)
                    partHashCalcFutures.add(calculatePartitionHashAsync(grpCtx, part, cpFlag));
            }

            return partHashCalcFutures;
        }

        /** */
        private Set<Integer> getGroupIds() {
            Collection<CacheGroupContext> cacheGroups = ignite.context().cache().cacheGroups();

            Set<Integer> grpIds = new CachesFiltering(cacheGroups)
                .filter(this::filterByCacheNames)
                .filter(this::filterByCacheFilter)
                .filter(this::filterByExcludeCaches)
                .result();

            if (F.isEmpty(grpIds))
                throw new NoMatchingCachesException();

            return grpIds;
        }

        /**
         * Filters cache groups by exclude regexps.
         *
         * @param cachesToFilter cache groups to filter
         */
        private void filterByExcludeCaches(Set<CacheGroupContext> cachesToFilter) {
            if (!F.isEmpty(arg.getExcludeCaches())) {
                Set<Pattern> excludedNamesPatterns = new HashSet<>();

                for (String excluded : arg.getExcludeCaches())
                    excludedNamesPatterns.add(Pattern.compile(excluded));

                cachesToFilter.removeIf(grp -> doesGrpMatchOneOfPatterns(grp, excludedNamesPatterns));
            }
        }

        /**
         * Filters cache groups by cache filter, also removes system (if not specified in filter option)
         * and local caches.
         *
         * @param cachesToFilter cache groups to filter
         */
        private void filterByCacheFilter(Set<CacheGroupContext> cachesToFilter) {
            cachesToFilter.removeIf(grp -> !doesGrpMatchFilter(grp));
        }

        /**
         * Filters cache groups by whitelist of cache name regexps. By default, all cache groups are included.
         *
         * @param cachesToFilter cache groups to filter
         */
        private void filterByCacheNames(Set<CacheGroupContext> cachesToFilter) {
            if (!F.isEmpty(arg.getCaches())) {
                Set<Pattern> cacheNamesPatterns = new HashSet<>();

                for (String cacheNameRegexp : arg.getCaches())
                    cacheNamesPatterns.add(Pattern.compile(cacheNameRegexp));

                cachesToFilter.removeIf(grp -> !doesGrpMatchOneOfPatterns(grp, cacheNamesPatterns));
            }
        }

        /**
         * Checks does the given group match filter.
         *
         * @param grp cache group.
         * @return boolean result
         */
        private boolean doesGrpMatchFilter(CacheGroupContext grp) {
            for (GridCacheContext cacheCtx : grp.caches()) {
                DynamicCacheDescriptor desc = ignite.context().cache().cacheDescriptor(cacheCtx.name());

                if (desc.cacheConfiguration().getCacheMode() != LOCAL && isCacheMatchFilter(desc))
                    return true;
            }

            return false;
        }

        /**
         * Checks does the name of given cache group or some of the names of its caches
         * match at least one of regexp from set.
         *
         * @param grp cache group
         * @param patterns compiled regexp patterns
         * @return boolean result
         */
        private boolean doesGrpMatchOneOfPatterns(CacheGroupContext grp, Set<Pattern> patterns) {
            for (Pattern pattern : patterns) {
                if (grp.name() != null && pattern.matcher(grp.name()).matches())
                    return true;

                for (GridCacheContext cacheCtx : grp.caches())
                    if (cacheCtx.name() != null && pattern.matcher(cacheCtx.name()).matches())
                        return true;
            }

            return false;
        }

        /**
         * @param desc Cache descriptor.
         */
        private boolean isCacheMatchFilter(DynamicCacheDescriptor desc) {
            if (arg instanceof VisorIdleVerifyDumpTaskArg) {
                DataStorageConfiguration dsCfg = ignite.context().config().getDataStorageConfiguration();

                CacheConfiguration cc = desc.cacheConfiguration();

                VisorIdleVerifyDumpTaskArg vdta = (VisorIdleVerifyDumpTaskArg)arg;

                switch (vdta.getCacheFilterEnum()) {
                    case DEFAULT:
                        return desc.cacheType().userCache() || !F.isEmpty(arg.getCaches());

                    case USER:
                        return desc.cacheType().userCache();

                    case SYSTEM:
                        return !desc.cacheType().userCache();

                    case NOT_PERSISTENT:
                        return desc.cacheType().userCache() && !GridCacheUtils.isPersistentCache(cc, dsCfg);

                    case PERSISTENT:
                        return desc.cacheType().userCache() && GridCacheUtils.isPersistentCache(cc, dsCfg);

                    case ALL:
                        break;

                    default:
                        assert false : "Illegal cache filter: " + vdta.getCacheFilterEnum();
                }
            }

            return true;
        }

        /**
         * @param grpCtx Group context.
         * @param part Local partition.
         * @param cpFlag Checkpoint flag.
         */
        private Future<Map<PartitionKeyV2, PartitionHashRecordV2>> calculatePartitionHashAsync(
            final CacheGroupContext grpCtx,
            final GridDhtLocalPartition part,
            AtomicBoolean cpFlag
        ) {
            return ForkJoinPool.commonPool().submit(() -> calculatePartitionHash(grpCtx, part, cpFlag));
        }

        /**
         * @param grpCtx Group context.
         * @param part Local partition.
         * @param cpFlag Checkpoint flag.
         */
        private Map<PartitionKeyV2, PartitionHashRecordV2> calculatePartitionHash(
            CacheGroupContext grpCtx,
            GridDhtLocalPartition part,
            AtomicBoolean cpFlag
        ) {
            if (!part.reserve())
                return Collections.emptyMap();

            int partHash = 0;
            long partSize;
            long updateCntrBefore = part.updateCounter();

            PartitionKeyV2 partKey = new PartitionKeyV2(grpCtx.groupId(), part.id(), grpCtx.cacheOrGroupName());

            Object consId = ignite.context().discovery().localNode().consistentId();

            boolean isPrimary = part.primary(grpCtx.topology().readyTopologyVersion());

            try {
                if (part.state() == GridDhtPartitionState.MOVING) {
                    PartitionHashRecordV2 movingHashRecord = new PartitionHashRecordV2(partKey, isPrimary, consId,
                        partHash, updateCntrBefore, PartitionHashRecordV2.MOVING_PARTITION_SIZE);

                    return Collections.singletonMap(partKey, movingHashRecord);
                }
                else if (part.state() != GridDhtPartitionState.OWNING)
                    return Collections.emptyMap();

                partSize = part.dataStore().fullSize();

                if (arg.isCheckCrc())
                    checkPartitionCrc(grpCtx, part, cpFlag);

                GridIterator<CacheDataRow> it = grpCtx.offheap().partitionIterator(part.id());

                while (it.hasNextX()) {
                    CacheDataRow row = it.nextX();

                    partHash += row.key().hashCode();

                    partHash += Arrays.hashCode(row.value().valueBytes(grpCtx.cacheObjectContext()));
                }

                long updateCntrAfter = part.updateCounter();

                if (updateCntrBefore != updateCntrAfter) {
                    throw new GridNotIdleException("Update counter of partition [grpId=" +
                        grpCtx.groupId() + ", partId=" + part.id() + "] changed during hash calculation [before=" +
                        updateCntrBefore + ", after=" + updateCntrAfter + "]");
                }
            }
            catch (IgniteCheckedException e) {
                U.error(log, "Can't calculate partition hash [grpId=" + grpCtx.groupId() +
                    ", partId=" + part.id() + "]", e);

                throw new IgniteException("Can't calculate partition hash [grpId=" + grpCtx.groupId() +
                    ", partId=" + part.id() + "]", e);
            }
            finally {
                part.release();
            }

            PartitionHashRecordV2 partRec =
                new PartitionHashRecordV2(partKey, isPrimary, consId, partHash, updateCntrBefore, partSize);

            completionCntr.incrementAndGet();

            return Collections.singletonMap(partKey, partRec);
        }

        /**
         * Checks correct CRC sum for given partition and cache group.
         *
         * @param grpCtx Cache group context
         * @param part partition.
         * @param cpFlag Checkpoint flag.
         */
        private void checkPartitionCrc(CacheGroupContext grpCtx, GridDhtLocalPartition part, AtomicBoolean cpFlag) {
            if (grpCtx.persistenceEnabled()) {
                FilePageStore pageStore = null;

                try {
                    FilePageStoreManager pageStoreMgr =
                        (FilePageStoreManager)ignite.context().cache().context().pageStore();

                    if (pageStoreMgr == null)
                        return;

                    pageStore = (FilePageStore)pageStoreMgr.getStore(grpCtx.groupId(), part.id());

                    IdleVerifyUtility.checkPartitionsPageCrcSum(pageStore, grpCtx, part.id(), FLAG_DATA, cpFlag);
                }
                catch (GridNotIdleException e) {
                    throw e;
                }
                catch (Exception | AssertionError e) {
                    if (cpFlag.get())
                        throw new GridNotIdleException("Checkpoint with dirty pages started! Cluster not idle!", e);

                    String msg = new SB("CRC check of partition: ").a(part.id()).a(", for cache group ")
                        .a(grpCtx.cacheOrGroupName()).a(" failed.")
                        .a(pageStore != null ? " file: " + pageStore.getFileAbsolutePath() : "").toString();

                    log.error(msg, e);

                    throw new IgniteException(msg, e);
                }
            }
        }
    }
}
