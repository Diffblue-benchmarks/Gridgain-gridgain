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

package org.apache.ignite.internal.processors.cache.distributed.dht;

import java.io.Externalizable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.UUID;
import org.apache.ignite.IgniteCheckedException;
import org.apache.ignite.IgniteException;
import org.apache.ignite.cluster.ClusterNode;
import org.apache.ignite.cluster.ClusterTopologyException;
import org.apache.ignite.internal.IgniteInternalFuture;
import org.apache.ignite.internal.NodeStoppingException;
import org.apache.ignite.internal.cluster.ClusterTopologyCheckedException;
import org.apache.ignite.internal.processors.affinity.AffinityTopologyVersion;
import org.apache.ignite.internal.processors.cache.CacheEntryPredicate;
import org.apache.ignite.internal.processors.cache.CacheObject;
import org.apache.ignite.internal.processors.cache.CacheOperationContext;
import org.apache.ignite.internal.processors.cache.GridCacheConcurrentMap;
import org.apache.ignite.internal.processors.cache.GridCacheContext;
import org.apache.ignite.internal.processors.cache.GridCacheEntryEx;
import org.apache.ignite.internal.processors.cache.GridCacheEntryInfo;
import org.apache.ignite.internal.processors.cache.GridCacheEntryRemovedException;
import org.apache.ignite.internal.processors.cache.GridCacheLockTimeoutException;
import org.apache.ignite.internal.processors.cache.GridCacheMvccCandidate;
import org.apache.ignite.internal.processors.cache.GridCacheReturn;
import org.apache.ignite.internal.processors.cache.KeyCacheObject;
import org.apache.ignite.internal.processors.cache.distributed.GridDistributedCacheEntry;
import org.apache.ignite.internal.processors.cache.distributed.GridDistributedLockCancelledException;
import org.apache.ignite.internal.processors.cache.distributed.GridDistributedUnlockRequest;
import org.apache.ignite.internal.processors.cache.distributed.dht.preloader.GridDhtForceKeysRequest;
import org.apache.ignite.internal.processors.cache.distributed.dht.preloader.GridDhtForceKeysResponse;
import org.apache.ignite.internal.processors.cache.distributed.dht.topology.GridDhtInvalidPartitionException;
import org.apache.ignite.internal.processors.cache.distributed.dht.topology.GridDhtLocalPartition;
import org.apache.ignite.internal.processors.cache.distributed.dht.topology.GridDhtPartitionTopology;
import org.apache.ignite.internal.processors.cache.distributed.near.GridNearGetRequest;
import org.apache.ignite.internal.processors.cache.distributed.near.GridNearLockRequest;
import org.apache.ignite.internal.processors.cache.distributed.near.GridNearLockResponse;
import org.apache.ignite.internal.processors.cache.distributed.near.GridNearSingleGetRequest;
import org.apache.ignite.internal.processors.cache.distributed.near.GridNearTransactionalCache;
import org.apache.ignite.internal.processors.cache.distributed.near.GridNearTxEnlistFuture;
import org.apache.ignite.internal.processors.cache.distributed.near.GridNearTxEnlistRequest;
import org.apache.ignite.internal.processors.cache.distributed.near.GridNearTxEnlistResponse;
import org.apache.ignite.internal.processors.cache.distributed.near.GridNearTxQueryEnlistFuture;
import org.apache.ignite.internal.processors.cache.distributed.near.GridNearTxQueryEnlistRequest;
import org.apache.ignite.internal.processors.cache.distributed.near.GridNearTxQueryEnlistResponse;
import org.apache.ignite.internal.processors.cache.distributed.near.GridNearTxQueryResultsEnlistFuture;
import org.apache.ignite.internal.processors.cache.distributed.near.GridNearTxQueryResultsEnlistRequest;
import org.apache.ignite.internal.processors.cache.distributed.near.GridNearTxQueryResultsEnlistResponse;
import org.apache.ignite.internal.processors.cache.distributed.near.GridNearTxRemote;
import org.apache.ignite.internal.processors.cache.distributed.near.GridNearUnlockRequest;
import org.apache.ignite.internal.processors.cache.mvcc.MvccSnapshot;
import org.apache.ignite.internal.processors.cache.mvcc.MvccSnapshotWithoutTxs;
import org.apache.ignite.internal.processors.cache.transactions.IgniteTxEntry;
import org.apache.ignite.internal.processors.cache.transactions.IgniteTxKey;
import org.apache.ignite.internal.processors.cache.transactions.IgniteTxLocalEx;
import org.apache.ignite.internal.processors.cache.version.GridCacheVersion;
import org.apache.ignite.internal.transactions.IgniteTxRollbackCheckedException;
import org.apache.ignite.internal.transactions.IgniteTxTimeoutCheckedException;
import org.apache.ignite.internal.util.F0;
import org.apache.ignite.internal.util.GridLeanSet;
import org.apache.ignite.internal.util.future.GridFinishedFuture;
import org.apache.ignite.internal.util.lang.GridClosureException;
import org.apache.ignite.internal.util.lang.IgnitePair;
import org.apache.ignite.internal.util.typedef.C1;
import org.apache.ignite.internal.util.typedef.C2;
import org.apache.ignite.internal.util.typedef.CI1;
import org.apache.ignite.internal.util.typedef.CI2;
import org.apache.ignite.internal.util.typedef.F;
import org.apache.ignite.internal.util.typedef.X;
import org.apache.ignite.internal.util.typedef.internal.CU;
import org.apache.ignite.internal.util.typedef.internal.U;
import org.apache.ignite.lang.IgniteUuid;
import org.apache.ignite.thread.IgniteThread;
import org.apache.ignite.transactions.TransactionIsolation;
import org.jetbrains.annotations.Nullable;

import static org.apache.ignite.cache.CacheWriteSynchronizationMode.FULL_SYNC;
import static org.apache.ignite.internal.processors.cache.GridCacheOperation.NOOP;
import static org.apache.ignite.internal.processors.cache.GridCacheUtils.isNearEnabled;
import static org.apache.ignite.internal.processors.cache.mvcc.MvccUtils.MVCC_OP_COUNTER_NA;
import static org.apache.ignite.transactions.TransactionConcurrency.PESSIMISTIC;
import static org.apache.ignite.transactions.TransactionIsolation.REPEATABLE_READ;
import static org.apache.ignite.transactions.TransactionState.COMMITTING;

/**
 * Base class for transactional DHT caches.
 */
@SuppressWarnings("unchecked")
public abstract class GridDhtTransactionalCacheAdapter<K, V> extends GridDhtCacheAdapter<K, V> {
    /** */
    private static final long serialVersionUID = 0L;

    /**
     * Empty constructor required for {@link Externalizable}.
     */
    protected GridDhtTransactionalCacheAdapter() {
        // No-op.
    }

    /**
     * @param ctx Context.
     */
    protected GridDhtTransactionalCacheAdapter(GridCacheContext<K, V> ctx) {
        super(ctx);
    }

    /**
     * Constructor used for near-only cache.
     *
     * @param ctx Cache context.
     * @param map Cache map.
     */
    protected GridDhtTransactionalCacheAdapter(GridCacheContext<K, V> ctx, GridCacheConcurrentMap map) {
        super(ctx, map);
    }

    /** {@inheritDoc} */
    @Override public void start() throws IgniteCheckedException {
        super.start();

        ctx.io().addCacheHandler(ctx.cacheId(), GridNearGetRequest.class, new CI2<UUID, GridNearGetRequest>() {
            @Override public void apply(UUID nodeId, GridNearGetRequest req) {
                processNearGetRequest(nodeId, req);
            }
        });

        ctx.io().addCacheHandler(ctx.cacheId(), GridNearSingleGetRequest.class, new CI2<UUID, GridNearSingleGetRequest>() {
            @Override public void apply(UUID nodeId, GridNearSingleGetRequest req) {
                processNearSingleGetRequest(nodeId, req);
            }
        });

        ctx.io().addCacheHandler(ctx.cacheId(), GridNearLockRequest.class, new CI2<UUID, GridNearLockRequest>() {
            @Override public void apply(UUID nodeId, GridNearLockRequest req) {
                processNearLockRequest(nodeId, req);
            }
        });

        ctx.io().addCacheHandler(ctx.cacheId(), GridDhtLockRequest.class, new CI2<UUID, GridDhtLockRequest>() {
            @Override public void apply(UUID nodeId, GridDhtLockRequest req) {
                processDhtLockRequest(nodeId, req);
            }
        });

        ctx.io().addCacheHandler(ctx.cacheId(), GridDhtLockResponse.class, new CI2<UUID, GridDhtLockResponse>() {
            @Override public void apply(UUID nodeId, GridDhtLockResponse req) {
                processDhtLockResponse(nodeId, req);
            }
        });

        ctx.io().addCacheHandler(ctx.cacheId(), GridNearUnlockRequest.class, new CI2<UUID, GridNearUnlockRequest>() {
            @Override public void apply(UUID nodeId, GridNearUnlockRequest req) {
                processNearUnlockRequest(nodeId, req);
            }
        });

        ctx.io().addCacheHandler(ctx.cacheId(), GridDhtUnlockRequest.class, new CI2<UUID, GridDhtUnlockRequest>() {
            @Override public void apply(UUID nodeId, GridDhtUnlockRequest req) {
                processDhtUnlockRequest(nodeId, req);
            }
        });

        ctx.io().addCacheHandler(ctx.cacheId(), GridNearTxQueryEnlistRequest.class, new CI2<UUID, GridNearTxQueryEnlistRequest>() {
            @Override public void apply(UUID nodeId, GridNearTxQueryEnlistRequest req) {
                processNearTxQueryEnlistRequest(nodeId, req);
            }
        });

        ctx.io().addCacheHandler(ctx.cacheId(), GridNearTxQueryEnlistResponse.class, new CI2<UUID, GridNearTxQueryEnlistResponse>() {
            @Override public void apply(UUID nodeId, GridNearTxQueryEnlistResponse req) {
                processNearTxQueryEnlistResponse(nodeId, req);
            }
        });

        ctx.io().addCacheHandler(ctx.cacheId(), GridDhtForceKeysRequest.class,
            new MessageHandler<GridDhtForceKeysRequest>() {
                @Override public void onMessage(ClusterNode node, GridDhtForceKeysRequest msg) {
                    processForceKeysRequest(node, msg);
                }
            });

        ctx.io().addCacheHandler(ctx.cacheId(), GridDhtForceKeysResponse.class,
            new MessageHandler<GridDhtForceKeysResponse>() {
                @Override public void onMessage(ClusterNode node, GridDhtForceKeysResponse msg) {
                    processForceKeyResponse(node, msg);
                }
            });

        ctx.io().addCacheHandler(ctx.cacheId(), GridNearTxQueryResultsEnlistRequest.class,
            new CI2<UUID, GridNearTxQueryResultsEnlistRequest>() {
                @Override public void apply(UUID nodeId, GridNearTxQueryResultsEnlistRequest req) {
                    processNearTxQueryResultsEnlistRequest(nodeId, req);
                }
            });

        ctx.io().addCacheHandler(ctx.cacheId(), GridNearTxQueryResultsEnlistResponse.class,
            new CI2<UUID, GridNearTxQueryResultsEnlistResponse>() {
                @Override public void apply(UUID nodeId, GridNearTxQueryResultsEnlistResponse req) {
                    processNearTxQueryResultsEnlistResponse(nodeId, req);
                }
            });

        ctx.io().addCacheHandler(ctx.cacheId(), GridNearTxEnlistRequest.class,
            new CI2<UUID, GridNearTxEnlistRequest>() {
                @Override public void apply(UUID nodeId, GridNearTxEnlistRequest req) {
                    processNearTxEnlistRequest(nodeId, req);
                }
            });

        ctx.io().addCacheHandler(ctx.cacheId(), GridNearTxEnlistResponse.class,
            new CI2<UUID, GridNearTxEnlistResponse>() {
                @Override public void apply(UUID nodeId, GridNearTxEnlistResponse msg) {
                    processNearTxEnlistResponse(nodeId, msg);
                }
            });

        ctx.io().addCacheHandler(ctx.cacheId(), GridDhtTxQueryEnlistRequest.class,
            new CI2<UUID, GridDhtTxQueryEnlistRequest>() {
                @Override public void apply(UUID nodeId, GridDhtTxQueryEnlistRequest msg) {
                    processDhtTxQueryEnlistRequest(nodeId, msg, false);
                }
            });

        ctx.io().addCacheHandler(ctx.cacheId(), GridDhtTxQueryFirstEnlistRequest.class,
            new CI2<UUID, GridDhtTxQueryEnlistRequest>() {
                @Override public void apply(UUID nodeId, GridDhtTxQueryEnlistRequest msg) {
                    processDhtTxQueryEnlistRequest(nodeId, msg, true);
                }
            });

        ctx.io().addCacheHandler(ctx.cacheId(), GridDhtTxQueryEnlistResponse.class,
            new CI2<UUID, GridDhtTxQueryEnlistResponse>() {
                @Override public void apply(UUID nodeId, GridDhtTxQueryEnlistResponse msg) {
                    processDhtTxQueryEnlistResponse(nodeId, msg);
                }
            });
    }

    /** {@inheritDoc} */
    @Override public abstract GridNearTransactionalCache<K, V> near();

    /**
     * @param nodeId Primary node ID.
     * @param req Request.
     * @param res Response.
     * @return Remote transaction.
     * @throws IgniteCheckedException If failed.
     * @throws GridDistributedLockCancelledException If lock has been cancelled.
     */
    @Nullable private GridDhtTxRemote startRemoteTx(UUID nodeId,
        GridDhtLockRequest req,
        GridDhtLockResponse res)
        throws IgniteCheckedException, GridDistributedLockCancelledException {
        List<KeyCacheObject> keys = req.keys();
        GridDhtTxRemote tx = null;

        int size = F.size(keys);

        for (int i = 0; i < size; i++) {
            KeyCacheObject key = keys.get(i);

            if (key == null)
                continue;

            IgniteTxKey txKey = ctx.txKey(key);

            if (log.isDebugEnabled())
                log.debug("Unmarshalled key: " + key);

            GridDistributedCacheEntry entry = null;

            while (true) {
                try {
                    int part = ctx.affinity().partition(key);

                    GridDhtLocalPartition locPart = ctx.topology().localPartition(part, req.topologyVersion(),
                        false);

                    if (locPart == null || !locPart.reserve()) {
                        if (log.isDebugEnabled())
                            log.debug("Local partition for given key is already evicted (will add to invalid " +
                                "partition list) [key=" + key + ", part=" + part + ", locPart=" + locPart + ']');

                        res.addInvalidPartition(part);

                        // Invalidate key in near cache, if any.
                        if (isNearEnabled(cacheCfg))
                            obsoleteNearEntry(key);

                        break;
                    }

                    try {
                        // Handle implicit locks for pessimistic transactions.
                        if (req.inTx()) {
                            if (tx == null)
                                tx = ctx.tm().tx(req.version());

                            if (tx == null) {
                                tx = new GridDhtTxRemote(
                                    ctx.shared(),
                                    req.nodeId(),
                                    req.futureId(),
                                    nodeId,
                                    req.nearXidVersion(),
                                    req.topologyVersion(),
                                    req.version(),
                                    /*commitVer*/null,
                                    ctx.systemTx(),
                                    ctx.ioPolicy(),
                                    PESSIMISTIC,
                                    req.isolation(),
                                    req.isInvalidate(),
                                    req.timeout(),
                                    req.txSize(),
                                    req.subjectId(),
                                    req.taskNameHash(),
                                    !req.skipStore() && req.storeUsed(),
                                    req.txLabel());

                                tx = ctx.tm().onCreated(null, tx);

                                if (tx == null || !ctx.tm().onStarted(tx))
                                    throw new IgniteTxRollbackCheckedException("Failed to acquire lock (transaction " +
                                        "has been completed) [ver=" + req.version() + ", tx=" + tx + ']');
                            }

                            tx.addWrite(
                                ctx,
                                NOOP,
                                txKey,
                                null,
                                null,
                                req.accessTtl(),
                                req.skipStore(),
                                req.keepBinary());
                        }

                        entry = entryExx(key, req.topologyVersion());

                        // Add remote candidate before reordering.
                        entry.addRemote(
                            req.nodeId(),
                            nodeId,
                            req.threadId(),
                            req.version(),
                            tx != null,
                            tx != null && tx.implicitSingle(),
                            null
                        );

                        // Invalidate key in near cache, if any.
                        if (isNearEnabled(cacheCfg) && req.invalidateNearEntry(i))
                            invalidateNearEntry(key, req.version());

                        // Get entry info after candidate is added.
                        if (req.needPreloadKey(i)) {
                            entry.unswap();

                            GridCacheEntryInfo info = entry.info();

                            if (info != null && !info.isNew() && !info.isDeleted())
                                res.addPreloadEntry(info);
                        }

                        // Double-check in case if sender node left the grid.
                        if (ctx.discovery().node(req.nodeId()) == null) {
                            if (log.isDebugEnabled())
                                log.debug("Node requesting lock left grid (lock request will be ignored): " + req);

                            entry.removeLock(req.version());

                            if (tx != null) {
                                tx.clearEntry(txKey);

                                // If there is a concurrent salvage, there could be a case when tx is moved to
                                // COMMITTING state, but this lock is never acquired.
                                if (tx.state() == COMMITTING)
                                    tx.forceCommit();
                                else
                                    tx.rollbackRemoteTx();
                            }

                            return null;
                        }

                        // Entry is legit.
                        break;
                    }
                    finally {
                        locPart.release();
                    }
                }
                catch (GridDhtInvalidPartitionException e) {
                    if (log.isDebugEnabled())
                        log.debug("Received invalid partition exception [e=" + e + ", req=" + req + ']');

                    res.addInvalidPartition(e.partition());

                    // Invalidate key in near cache, if any.
                    if (isNearEnabled(cacheCfg))
                        obsoleteNearEntry(key);

                    if (tx != null) {
                        tx.clearEntry(txKey);

                        if (log.isDebugEnabled())
                            log.debug("Cleared invalid entry from remote transaction (will skip) [entry=" +
                                entry + ", tx=" + tx + ']');
                    }

                    break;
                }
                catch (GridCacheEntryRemovedException ignored) {
                    assert entry.obsoleteVersion() != null : "Obsolete flag not set on removed entry: " +
                        entry;

                    if (log.isDebugEnabled())
                        log.debug("Received entry removed exception (will retry on renewed entry): " + entry);

                    if (tx != null) {
                        tx.clearEntry(txKey);

                        if (log.isDebugEnabled())
                            log.debug("Cleared removed entry from remote transaction (will retry) [entry=" +
                                entry + ", tx=" + tx + ']');
                    }
                }
            }
        }

        if (tx != null && tx.empty()) {
            if (log.isDebugEnabled())
                log.debug("Rolling back remote DHT transaction because it is empty [req=" + req + ", res=" + res + ']');

            tx.rollbackRemoteTx();

            tx = null;
        }

        return tx;
    }

    /**
     * @param nodeId Node ID.
     * @param req Request.
     */
    private void processDhtLockRequest(final UUID nodeId, final GridDhtLockRequest req) {
        if (txLockMsgLog.isDebugEnabled()) {
            txLockMsgLog.debug("Received dht lock request [txId=" + req.nearXidVersion() +
                ", dhtTxId=" + req.version() +
                ", inTx=" + req.inTx() +
                ", node=" + nodeId + ']');
        }

        IgniteInternalFuture<Object> keyFut = F.isEmpty(req.keys()) ? null :
            ctx.group().preloader().request(ctx, req.keys(), req.topologyVersion());

        if (keyFut == null || keyFut.isDone()) {
            if (keyFut != null) {
                try {
                    keyFut.get();
                }
                catch (NodeStoppingException ignored) {
                    return;
                }
                catch (IgniteCheckedException e) {
                    onForceKeysError(nodeId, req, e);

                    return;
                }
            }

            processDhtLockRequest0(nodeId, req);
        }
        else {
            keyFut.listen(new CI1<IgniteInternalFuture<Object>>() {
                @Override public void apply(IgniteInternalFuture<Object> fut) {
                    try {
                        fut.get();
                    }
                    catch (NodeStoppingException ignored) {
                        return;
                    }
                    catch (IgniteCheckedException e) {
                        onForceKeysError(nodeId, req, e);

                        return;
                    }

                    processDhtLockRequest0(nodeId, req);
                }
            });
        }
    }

    /**
     * @param nodeId Node ID.
     * @param req Request.
     * @param e Error.
     */
    private void onForceKeysError(UUID nodeId, GridDhtLockRequest req, IgniteCheckedException e) {
        GridDhtLockResponse res = new GridDhtLockResponse(ctx.cacheId(),
            req.version(),
            req.futureId(),
            req.miniId(),
            e,
            ctx.deploymentEnabled());

        try {
            ctx.io().send(nodeId, res, ctx.ioPolicy());
        }
        catch (ClusterTopologyCheckedException ignored) {
            if (log.isDebugEnabled())
                log.debug("Failed to send lock reply to remote node because it left grid: " + nodeId);
        }
        catch (IgniteCheckedException ignored) {
            U.error(log, "Failed to send lock reply to node: " + nodeId, e);
        }
    }

    /**
     * @param nodeId Node ID.
     * @param req Request.
     */
    private void processDhtLockRequest0(UUID nodeId, GridDhtLockRequest req) {
        assert nodeId != null;
        assert req != null;
        assert !nodeId.equals(locNodeId);

        int cnt = F.size(req.keys());

        GridDhtLockResponse res;

        GridDhtTxRemote dhtTx = null;
        GridNearTxRemote nearTx = null;

        boolean fail = false;
        boolean cancelled = false;

        try {
            res = new GridDhtLockResponse(ctx.cacheId(), req.version(), req.futureId(), req.miniId(), cnt,
                ctx.deploymentEnabled());

            dhtTx = startRemoteTx(nodeId, req, res);
            nearTx = isNearEnabled(cacheCfg) ? near().startRemoteTx(nodeId, req) : null;

            if (nearTx != null && !nearTx.empty())
                res.nearEvicted(nearTx.evicted());
            else {
                if (!F.isEmpty(req.nearKeys())) {
                    Collection<IgniteTxKey> nearEvicted = new ArrayList<>(req.nearKeys().size());

                    nearEvicted.addAll(F.viewReadOnly(req.nearKeys(), new C1<KeyCacheObject, IgniteTxKey>() {
                        @Override public IgniteTxKey apply(KeyCacheObject k) {
                            return ctx.txKey(k);
                        }
                    }));

                    res.nearEvicted(nearEvicted);
                }
            }
        }
        catch (IgniteTxRollbackCheckedException e) {
            String err = "Failed processing DHT lock request (transaction has been completed): " + req;

            U.error(log, err, e);

            res = new GridDhtLockResponse(ctx.cacheId(), req.version(), req.futureId(), req.miniId(),
                new IgniteTxRollbackCheckedException(err, e), ctx.deploymentEnabled());

            fail = true;
        }
        catch (IgniteCheckedException e) {
            String err = "Failed processing DHT lock request: " + req;

            U.error(log, err, e);

            res = new GridDhtLockResponse(ctx.cacheId(),
                req.version(),
                req.futureId(),
                req.miniId(),
                new IgniteCheckedException(err, e), ctx.deploymentEnabled());

            fail = true;
        }
        catch (GridDistributedLockCancelledException ignored) {
            // Received lock request for cancelled lock.
            if (log.isDebugEnabled())
                log.debug("Received lock request for canceled lock (will ignore): " + req);

            res = null;

            fail = true;
            cancelled = true;
        }

        boolean releaseAll = false;

        if (res != null) {
            try {
                // Reply back to sender.
                ctx.io().send(nodeId, res, ctx.ioPolicy());

                if (txLockMsgLog.isDebugEnabled()) {
                    txLockMsgLog.debug("Sent dht lock response [txId=" + req.nearXidVersion() +
                        ", dhtTxId=" + req.version() +
                        ", inTx=" + req.inTx() +
                        ", node=" + nodeId + ']');
                }
            }
            catch (ClusterTopologyCheckedException ignored) {
                U.warn(txLockMsgLog, "Failed to send dht lock response, node failed [" +
                    "txId=" + req.nearXidVersion() +
                    ", dhtTxId=" + req.version() +
                    ", inTx=" + req.inTx() +
                    ", node=" + nodeId + ']');

                fail = true;
                releaseAll = true;
            }
            catch (IgniteCheckedException e) {
                U.error(txLockMsgLog, "Failed to send dht lock response (lock will not be acquired) " +
                    "txId=" + req.nearXidVersion() +
                    ", dhtTxId=" + req.version() +
                    ", inTx=" + req.inTx() +
                    ", node=" + nodeId + ']', e);

                fail = true;
            }
        }

        if (fail) {
            if (dhtTx != null)
                dhtTx.rollbackRemoteTx();

            if (nearTx != null) // Even though this should never happen, we leave this check for consistency.
                nearTx.rollbackRemoteTx();

            List<KeyCacheObject> keys = req.keys();

            if (keys != null) {
                for (KeyCacheObject key : keys) {
                    while (true) {
                        GridDistributedCacheEntry entry = peekExx(key);

                        try {
                            if (entry != null) {
                                // Release all locks because sender node left grid.
                                if (releaseAll)
                                    entry.removeExplicitNodeLocks(req.nodeId());
                                else
                                    entry.removeLock(req.version());
                            }

                            break;
                        }
                        catch (GridCacheEntryRemovedException ignore) {
                            if (log.isDebugEnabled())
                                log.debug("Attempted to remove lock on removed entity during during failure " +
                                    "handling for dht lock request (will retry): " + entry);
                        }
                    }
                }
            }

            if (releaseAll && !cancelled)
                U.warn(log, "Sender node left grid in the midst of lock acquisition (locks have been released).");
        }
    }

    /**
     * @param nodeId Node ID.
     * @param req Request.
     */
    private void processDhtUnlockRequest(UUID nodeId, GridDhtUnlockRequest req) {
        clearLocks(nodeId, req);

        if (isNearEnabled(cacheCfg))
            near().clearLocks(nodeId, req);
    }

    /**
     * @param nodeId Node ID.
     * @param req Request.
     */
    private void processNearTxQueryEnlistRequest(UUID nodeId, final GridNearTxQueryEnlistRequest req) {
        assert nodeId != null;
        assert req != null;

        ClusterNode nearNode = ctx.discovery().node(nodeId);

        GridDhtTxLocal tx;

        try {
            tx = initTxTopologyVersion(nodeId,
                nearNode,
                req.version(),
                req.futureId(),
                req.miniId(),
                req.firstClientRequest(),
                req.topologyVersion(),
                req.threadId(),
                req.txTimeout(),
                req.subjectId(),
                req.taskNameHash(),
                req.mvccSnapshot());
        }
        catch (IgniteCheckedException | IgniteException ex) {
            GridNearTxQueryEnlistResponse res = new GridNearTxQueryEnlistResponse(req.cacheId(),
                req.futureId(),
                req.miniId(),
                req.version(),
                ex);

            try {
                ctx.io().send(nearNode, res, ctx.ioPolicy());
            }
            catch (IgniteCheckedException e) {
                U.error(log, "Failed to send near enlist response [" +
                    "txId=" + req.version() +
                    ", node=" + nodeId +
                    ", res=" + res + ']', e);
            }

            return;
        }

        GridDhtTxQueryEnlistFuture fut = new GridDhtTxQueryEnlistFuture(
            nodeId,
            req.version(),
            req.mvccSnapshot(),
            req.threadId(),
            req.futureId(),
            req.miniId(),
            tx,
            req.cacheIds(),
            req.partitions(),
            req.schemaName(),
            req.query(),
            req.parameters(),
            req.flags(),
            req.pageSize(),
            req.timeout(),
            ctx);

        fut.listen(NearTxQueryEnlistResultHandler.instance());

        fut.init();
    }

    /**
     * @param nodeId Node ID.
     * @param req Request.
     */
    private void processNearLockRequest(UUID nodeId, GridNearLockRequest req) {
        assert ctx.affinityNode();
        assert nodeId != null;
        assert req != null;

        if (txLockMsgLog.isDebugEnabled()) {
            txLockMsgLog.debug("Received near lock request [txId=" + req.version() +
                ", inTx=" + req.inTx() +
                ", node=" + nodeId + ']');
        }

        ClusterNode nearNode = ctx.discovery().node(nodeId);

        if (nearNode == null) {
            U.warn(txLockMsgLog, "Received near lock request from unknown node (will ignore) [txId=" + req.version() +
                ", inTx=" + req.inTx() +
                ", node=" + nodeId + ']');

            return;
        }

        processNearLockRequest0(nearNode, req);
    }

    /**
     * @param nearNode
     * @param req
     */
    private void processNearLockRequest0(ClusterNode nearNode, GridNearLockRequest req) {
        IgniteInternalFuture<?> f;

        if (req.firstClientRequest()) {
            for (; ; ) {
                if (waitForExchangeFuture(nearNode, req))
                    return;

                f = lockAllAsync(ctx, nearNode, req, null);

                if (f != null)
                    break;
            }
        }
        else
            f = lockAllAsync(ctx, nearNode, req, null);

        // Register listener just so we print out errors.
        // Exclude lock timeout and rollback exceptions since it's not a fatal exception.
        f.listen(CU.errorLogger(log, GridCacheLockTimeoutException.class,
            GridDistributedLockCancelledException.class, IgniteTxTimeoutCheckedException.class,
            IgniteTxRollbackCheckedException.class));
    }

    /**
     * @param node Node.
     * @param req Request.
     */
    private boolean waitForExchangeFuture(final ClusterNode node, final GridNearLockRequest req) {
        assert req.firstClientRequest() : req;

        GridDhtTopologyFuture topFut = ctx.shared().exchange().lastTopologyFuture();

        if (!topFut.isDone()) {
            Thread curThread = Thread.currentThread();

            if (curThread instanceof IgniteThread) {
                final IgniteThread thread = (IgniteThread)curThread;

                if (thread.cachePoolThread()) {
                    // Near transaction's finish on timeout will unlock topFut if it was held for too long,
                    // so need to listen with timeout. This is not true for optimistic transactions.
                    topFut.listen(new CI1<IgniteInternalFuture<AffinityTopologyVersion>>() {
                        @Override public void apply(IgniteInternalFuture<AffinityTopologyVersion> fut) {
                            ctx.kernalContext().closure().runLocalWithThreadPolicy(thread, new Runnable() {
                                @Override public void run() {
                                    try {
                                        processNearLockRequest0(node, req);
                                    }
                                    finally {
                                        ctx.io().onMessageProcessed(req);
                                    }
                                }
                            });
                        }
                    });

                    return true;
                }
            }

            try {
                topFut.get();
            }
            catch (IgniteCheckedException e) {
                U.error(log, "Topology future failed: " + e, e);
            }
        }

        return false;
    }

    /**
     * @param nodeId Node ID.
     * @param res Response.
     */
    private void processDhtLockResponse(UUID nodeId, GridDhtLockResponse res) {
        assert nodeId != null;
        assert res != null;
        GridDhtLockFuture fut = (GridDhtLockFuture)ctx.mvcc().<Boolean>versionedFuture(res.version(), res.futureId());

        if (fut == null) {
            if (txLockMsgLog.isDebugEnabled())
                txLockMsgLog.debug("Received dht lock response for unknown future [txId=null" +
                    ", dhtTxId=" + res.version() +
                    ", node=" + nodeId + ']');

            return;
        }
        else if (txLockMsgLog.isDebugEnabled()) {
            txLockMsgLog.debug("Received dht lock response [txId=" + fut.nearLockVersion() +
                ", dhtTxId=" + res.version() +
                ", node=" + nodeId + ']');
        }

        fut.onResult(nodeId, res);
    }

    /** {@inheritDoc} */
    @Override public IgniteInternalFuture<Boolean> lockAllAsync(
        @Nullable Collection<KeyCacheObject> keys,
        long timeout,
        IgniteTxLocalEx txx,
        boolean isInvalidate,
        boolean isRead,
        boolean retval,
        TransactionIsolation isolation,
        long createTtl,
        long accessTtl) {
        CacheOperationContext opCtx = ctx.operationContextPerCall();

        return lockAllAsyncInternal(
            keys,
            timeout,
            txx,
            isInvalidate,
            isRead,
            retval,
            isolation,
            createTtl,
            accessTtl,
            CU.empty0(),
            opCtx != null && opCtx.skipStore(),
            opCtx != null && opCtx.isKeepBinary());
    }

    /**
     * Acquires locks in partitioned cache.
     *
     * @param keys Keys to lock.
     * @param timeout Lock timeout.
     * @param txx Transaction.
     * @param isInvalidate Invalidate flag.
     * @param isRead Read flag.
     * @param retval Return value flag.
     * @param isolation Transaction isolation.
     * @param createTtl TTL for create operation.
     * @param accessTtl TTL for read operation.
     * @param filter Optional filter.
     * @param skipStore Skip store flag.
     * @return Lock future.
     */
    public GridDhtFuture<Boolean> lockAllAsyncInternal(@Nullable Collection<KeyCacheObject> keys,
        long timeout,
        IgniteTxLocalEx txx,
        boolean isInvalidate,
        boolean isRead,
        boolean retval,
        TransactionIsolation isolation,
        long createTtl,
        long accessTtl,
        CacheEntryPredicate[] filter,
        boolean skipStore,
        boolean keepBinary) {
        if (keys == null || keys.isEmpty())
            return new GridDhtFinishedFuture<>(true);

        GridDhtTxLocalAdapter tx = (GridDhtTxLocalAdapter)txx;

        assert tx != null;

        GridDhtLockFuture fut = new GridDhtLockFuture(
            ctx,
            tx.nearNodeId(),
            tx.nearXidVersion(),
            tx.topologyVersion(),
            keys.size(),
            isRead,
            retval,
            timeout,
            tx,
            tx.threadId(),
            createTtl,
            accessTtl,
            filter,
            skipStore,
            keepBinary);

        if (fut.isDone()) // Possible in case of cancellation or timeout or rollback.
            return fut;

        for (KeyCacheObject key : keys) {
            try {
                while (true) {
                    GridDhtCacheEntry entry = entryExx(key, tx.topologyVersion());

                    try {
                        fut.addEntry(entry);

                        // Possible in case of cancellation or time out or rollback.
                        if (fut.isDone())
                            return fut;

                        break;
                    }
                    catch (GridCacheEntryRemovedException ignore) {
                        if (log.isDebugEnabled())
                            log.debug("Got removed entry when adding lock (will retry): " + entry);
                    }
                    catch (GridDistributedLockCancelledException e) {
                        if (log.isDebugEnabled())
                            log.debug("Failed to add entry [err=" + e + ", entry=" + entry + ']');

                        return new GridDhtFinishedFuture<>(e);
                    }
                }
            }
            catch (GridDhtInvalidPartitionException e) {
                fut.addInvalidPartition(ctx, e.partition());

                if (log.isDebugEnabled())
                    log.debug("Added invalid partition to DHT lock future [part=" + e.partition() + ", fut=" +
                        fut + ']');
            }
        }

        if (!fut.isDone()) {
            ctx.mvcc().addFuture(fut);

            fut.map();
        }

        return fut;
    }

    /**
     * @param cacheCtx Cache context.
     * @param nearNode Near node.
     * @param req Request.
     * @param filter0 Filter.
     * @return Future.
     */
    public IgniteInternalFuture<GridNearLockResponse> lockAllAsync(
        final GridCacheContext<?, ?> cacheCtx,
        final ClusterNode nearNode,
        final GridNearLockRequest req,
        @Nullable final CacheEntryPredicate[] filter0) {
        final List<KeyCacheObject> keys = req.keys();

        CacheEntryPredicate[] filter = filter0;

        // Set message into thread context.
        GridDhtTxLocal tx = null;

        try {
            int cnt = keys.size();

            if (req.inTx()) {
                GridCacheVersion dhtVer = ctx.tm().mappedVersion(req.version());

                if (dhtVer != null)
                    tx = ctx.tm().tx(dhtVer);
            }

            final List<GridCacheEntryEx> entries = new ArrayList<>(cnt);

            // Unmarshal filter first.
            if (filter == null)
                filter = req.filter();

            GridDhtLockFuture fut = null;

            GridDhtPartitionTopology top = null;

            if (req.firstClientRequest()) {
                assert nearNode.isClient();

                top = topology();

                top.readLock();

                if (!top.topologyVersionFuture().isDone()) {
                    top.readUnlock();

                    return null;
                }
            }

            try {
                if (top != null && needRemap(req.topologyVersion(), top.readyTopologyVersion(), req.keys())) {
                    if (log.isDebugEnabled()) {
                        log.debug("Client topology version mismatch, need remap lock request [" +
                            "reqTopVer=" + req.topologyVersion() +
                            ", locTopVer=" + top.readyTopologyVersion() +
                            ", req=" + req + ']');
                    }

                    GridNearLockResponse res = sendClientLockRemapResponse(nearNode,
                        req,
                        top.lastTopologyChangeVersion());

                    return new GridFinishedFuture<>(res);
                }

                if (req.inTx()) {
                    if (tx == null) {
                        tx = new GridDhtTxLocal(
                            ctx.shared(),
                            req.topologyVersion(),
                            nearNode.id(),
                            req.version(),
                            req.futureId(),
                            req.miniId(),
                            req.threadId(),
                            /*implicitTx*/false,
                            /*implicitSingleTx*/false,
                            ctx.systemTx(),
                            false,
                            ctx.ioPolicy(),
                            PESSIMISTIC,
                            req.isolation(),
                            req.timeout(),
                            req.isInvalidate(),
                            !req.skipStore(),
                            false,
                            req.txSize(),
                            null,
                            req.subjectId(),
                            req.taskNameHash(),
                            req.txLabel(),
                            null);

                        if (req.syncCommit())
                            tx.syncMode(FULL_SYNC);

                        tx = ctx.tm().onCreated(null, tx);

                        if (tx == null || !tx.init()) {
                            String msg = "Failed to acquire lock (transaction has been completed): " +
                                req.version();

                            U.warn(log, msg);

                            if (tx != null)
                                tx.rollbackDhtLocal();

                            return new GridDhtFinishedFuture<>(new IgniteTxRollbackCheckedException(msg));
                        }

                        tx.topologyVersion(req.topologyVersion());
                    }
                }
                else {
                    fut = new GridDhtLockFuture(ctx,
                        nearNode.id(),
                        req.version(),
                        req.topologyVersion(),
                        cnt,
                        req.txRead(),
                        req.needReturnValue(),
                        req.timeout(),
                        tx,
                        req.threadId(),
                        req.createTtl(),
                        req.accessTtl(),
                        filter,
                        req.skipStore(),
                        req.keepBinary());

                    // Add before mapping.
                    if (!ctx.mvcc().addFuture(fut))
                        throw new IllegalStateException("Duplicate future ID: " + fut);
                }
            }
            finally {
                if (top != null)
                    top.readUnlock();
            }

            boolean timedOut = false;

            for (KeyCacheObject key : keys) {
                if (timedOut)
                    break;

                while (true) {
                    // Specify topology version to make sure containment is checked
                    // based on the requested version, not the latest.
                    GridDhtCacheEntry entry = entryExx(key, req.topologyVersion());

                    try {
                        if (fut != null) {
                            // This method will add local candidate.
                            // Entry cannot become obsolete after this method succeeded.
                            fut.addEntry(key == null ? null : entry);

                            if (fut.isDone()) {
                                timedOut = true;

                                break;
                            }
                        }

                        entries.add(entry);

                        break;
                    }
                    catch (GridCacheEntryRemovedException ignore) {
                        if (log.isDebugEnabled())
                            log.debug("Got removed entry when adding lock (will retry): " + entry);
                    }
                    catch (GridDistributedLockCancelledException e) {
                        if (log.isDebugEnabled())
                            log.debug("Got lock request for cancelled lock (will ignore): " +
                                entry);

                        fut.onError(e);

                        return new GridDhtFinishedFuture<>(e);
                    }
                }
            }

            // Handle implicit locks for pessimistic transactions.
            if (req.inTx()) {
                ctx.tm().txContext(tx);

                if (log.isDebugEnabled())
                    log.debug("Performing DHT lock [tx=" + tx + ", entries=" + entries + ']');

                IgniteInternalFuture<GridCacheReturn> txFut = tx.lockAllAsync(
                    cacheCtx,
                    entries,
                    req.messageId(),
                    req.txRead(),
                    req.needReturnValue(),
                    req.createTtl(),
                    req.accessTtl(),
                    req.skipStore(),
                    req.keepBinary(),
                    req.nearCache());

                final GridDhtTxLocal t = tx;

                return new GridDhtEmbeddedFuture(
                    txFut,
                    new C2<GridCacheReturn, Exception, IgniteInternalFuture<GridNearLockResponse>>() {
                        @Override public IgniteInternalFuture<GridNearLockResponse> apply(
                            GridCacheReturn o, Exception e) {
                            if (e != null)
                                e = U.unwrap(e);

                            // Transaction can be emptied by asynchronous rollback.
                            assert e != null || !t.empty();

                            // Create response while holding locks.
                            final GridNearLockResponse resp = createLockReply(nearNode,
                                entries,
                                req,
                                t,
                                t.xidVersion(),
                                e);

                            assert !t.implicit() : t;
                            assert !t.onePhaseCommit() : t;

                            sendLockReply(nearNode, t, req, resp);

                            return new GridFinishedFuture<>(resp);
                        }
                    }
                );
            }
            else {
                assert fut != null;

                // This will send remote messages.
                fut.map();

                final GridCacheVersion mappedVer = fut.version();

                return new GridDhtEmbeddedFuture<>(
                    new C2<Boolean, Exception, GridNearLockResponse>() {
                        @Override public GridNearLockResponse apply(Boolean b, Exception e) {
                            if (e != null)
                                e = U.unwrap(e);
                            else if (!b)
                                e = new GridCacheLockTimeoutException(req.version());

                            GridNearLockResponse res = createLockReply(nearNode,
                                entries,
                                req,
                                null,
                                mappedVer,
                                e);

                            sendLockReply(nearNode, null, req, res);

                            return res;
                        }
                    },
                    fut);
            }
        }
        catch (IgniteCheckedException | RuntimeException e) {
            String err = "Failed to unmarshal at least one of the keys for lock request message: " + req;

            U.error(log, err, e);

            if (tx != null) {
                try {
                    tx.rollbackDhtLocal();
                }
                catch (IgniteCheckedException ex) {
                    U.error(log, "Failed to rollback the transaction: " + tx, ex);
                }
            }

            try {
                GridNearLockResponse res = createLockReply(nearNode,
                    Collections.emptyList(),
                    req,
                    tx,
                    tx != null ? tx.xidVersion() : req.version(),
                    e);

                sendLockReply(nearNode, null, req, res);
            }
            catch (Exception ex) {
                U.error(log, "Failed to send response for request message: " + req, ex);
            }

            return new GridDhtFinishedFuture<>(
                new IgniteCheckedException(err, e));
        }
    }

    /**
     * @param nearNode Client node.
     * @param req Request.
     * @param topVer Remap version.
     * @return Response.
     */
    private GridNearLockResponse sendClientLockRemapResponse(ClusterNode nearNode,
        GridNearLockRequest req,
        AffinityTopologyVersion topVer) {
        assert topVer != null;

        GridNearLockResponse res = new GridNearLockResponse(
            ctx.cacheId(),
            req.version(),
            req.futureId(),
            req.miniId(),
            false,
            0,
            null,
            topVer,
            ctx.deploymentEnabled());

        try {
            ctx.io().send(nearNode, res, ctx.ioPolicy());
        }
        catch (ClusterTopologyCheckedException ignored) {
            if (log.isDebugEnabled())
                log.debug("Failed to send client lock remap response, client node failed " +
                    "[node=" + nearNode + ", req=" + req + ']');
        }
        catch (IgniteCheckedException e) {
            U.error(log, "Failed to send client lock remap response [node=" + nearNode + ", req=" + req + ']', e);
        }

        return res;
    }

    /**
     * @param nearNode Near node.
     * @param entries Entries.
     * @param req Lock request.
     * @param tx Transaction.
     * @param mappedVer Mapped version.
     * @param err Error.
     * @return Response.
     */
    private GridNearLockResponse createLockReply(
        ClusterNode nearNode,
        List<GridCacheEntryEx> entries,
        GridNearLockRequest req,
        @Nullable GridDhtTxLocalAdapter tx,
        GridCacheVersion mappedVer,
        Throwable err) {
        assert mappedVer != null;
        assert tx == null || tx.xidVersion().equals(mappedVer);

        try {
            // Send reply back to originating near node.
            GridNearLockResponse res = new GridNearLockResponse(ctx.cacheId(),
                req.version(),
                req.futureId(),
                req.miniId(),
                tx != null && tx.onePhaseCommit(),
                entries.size(),
                err,
                null,
                ctx.deploymentEnabled());

            if (err == null) {
                res.pending(localDhtPendingVersions(entries, mappedVer));

                // We have to add completed versions for cases when nearLocal and remote transactions
                // execute concurrently.
                IgnitePair<Collection<GridCacheVersion>> versPair = ctx.tm().versions(req.version());

                res.completedVersions(versPair.get1(), versPair.get2());

                int i = 0;

                for (ListIterator<GridCacheEntryEx> it = entries.listIterator(); it.hasNext(); ) {
                    GridCacheEntryEx e = it.next();

                    assert e != null;

                    while (true) {
                        try {
                            // Don't return anything for invalid partitions.
                            if (tx == null || !tx.isRollbackOnly()) {
                                GridCacheVersion dhtVer = req.dhtVersion(i);

                                GridCacheVersion ver = e.version();

                                boolean ret = req.returnValue(i) || dhtVer == null || !dhtVer.equals(ver);

                                CacheObject val = null;

                                if (ret) {
                                    val = e.innerGet(
                                        null,
                                        tx,
                                        /*read-through*/false,
                                        /*update-metrics*/true,
                                        /*event notification*/req.returnValue(i),
                                        CU.subjectId(tx, ctx.shared()),
                                        null,
                                        tx != null ? tx.resolveTaskName() : null,
                                        null,
                                        req.keepBinary());
                                }

                                assert e.lockedBy(mappedVer) ||
                                    ctx.mvcc().isRemoved(e.context(), mappedVer) ||
                                    tx != null && tx.isRollbackOnly():
                                    "Entry does not own lock for tx [locNodeId=" + ctx.localNodeId() +
                                        ", entry=" + e +
                                        ", mappedVer=" + mappedVer + ", ver=" + ver +
                                        ", tx=" + CU.txString(tx) + ", req=" + req + ']';

                                boolean filterPassed = false;

                                if (tx != null && tx.onePhaseCommit()) {
                                    IgniteTxEntry writeEntry = tx.entry(ctx.txKey(e.key()));

                                    assert writeEntry != null :
                                        "Missing tx entry for locked cache entry: " + e;

                                    filterPassed = writeEntry.filtersPassed();
                                }

                                if (ret && val == null)
                                    val = e.valueBytes(null);

                                // We include values into response since they are required for local
                                // calls and won't be serialized. We are also including DHT version.
                                res.addValueBytes(
                                    ret ? val : null,
                                    filterPassed,
                                    ver,
                                    mappedVer);
                            }
                            else {
                                // We include values into response since they are required for local
                                // calls and won't be serialized. We are also including DHT version.
                                res.addValueBytes(null, false, e.version(), mappedVer);
                            }

                            break;
                        }
                        catch (GridCacheEntryRemovedException ignore) {
                            if (log.isDebugEnabled())
                                log.debug("Got removed entry when sending reply to DHT lock request " +
                                    "(will retry): " + e);

                            e = entryExx(e.key());

                            it.set(e);
                        }
                    }

                    i++;
                }
            }

            return res;
        }
        catch (IgniteCheckedException e) {
            U.error(log, "Failed to get value for lock reply message for node [node=" +
                U.toShortString(nearNode) + ", req=" + req + ']', e);

            return new GridNearLockResponse(ctx.cacheId(),
                req.version(),
                req.futureId(),
                req.miniId(),
                false,
                entries.size(),
                e,
                null,
                ctx.deploymentEnabled());
        }
    }

    /**
     * Send lock reply back to near node.
     *
     * @param nearNode Near node.
     * @param tx Transaction.
     * @param req Lock request.
     * @param res Lock response.
     */
    private void sendLockReply(
        ClusterNode nearNode,
        @Nullable GridDhtTxLocal tx,
        GridNearLockRequest req,
        GridNearLockResponse res
    ) {
        Throwable err = res.error();

        // Log error before sending reply.
        if (err != null && !(err instanceof GridCacheLockTimeoutException) &&
            !(err instanceof IgniteTxRollbackCheckedException) && !ctx.kernalContext().isStopping())
            U.error(log, "Failed to acquire lock for request: " + req, err);

        try {
            // TODO Async rollback
            // Don't send reply message to this node or if lock was cancelled or tx was rolled back asynchronously.
            if (!nearNode.id().equals(ctx.nodeId()) && !X.hasCause(err, GridDistributedLockCancelledException.class) &&
                !X.hasCause(err, IgniteTxRollbackCheckedException.class)) {
                ctx.io().send(nearNode, res, ctx.ioPolicy());

                if (txLockMsgLog.isDebugEnabled()) {
                    txLockMsgLog.debug("Sent near lock response [txId=" + req.version() +
                        ", inTx=" + req.inTx() +
                        ", node=" + nearNode.id() + ']');
                }
            }
            else {
                if (txLockMsgLog.isDebugEnabled() && !nearNode.id().equals(ctx.nodeId())) {
                    txLockMsgLog.debug("Skip send near lock response [txId=" + req.version() +
                        ", inTx=" + req.inTx() +
                        ", node=" + nearNode.id() +
                        ", err=" + err + ']');
                }
            }
        }
        catch (IgniteCheckedException e) {
            U.error(txLockMsgLog, "Failed to send near lock response (will rollback transaction) [" +
                "txId=" + req.version() +
                ", inTx=" + req.inTx() +
                ", node=" + nearNode.id() +
                ", res=" + res + ']', e);

            if (tx != null)
                try {
                    tx.rollbackDhtLocalAsync();
                }
                catch (Throwable e1) {
                    e.addSuppressed(e1);
                }

            // Convert to closure exception as this method is only called form closures.
            throw new GridClosureException(e);
        }
    }

    /**
     * Collects versions of pending candidates versions less then base.
     *
     * @param entries Tx entries to process.
     * @param baseVer Base version.
     * @return Collection of pending candidates versions.
     */
    private Collection<GridCacheVersion> localDhtPendingVersions(Iterable<GridCacheEntryEx> entries,
        GridCacheVersion baseVer) {
        Collection<GridCacheVersion> lessPending = new GridLeanSet<>(5);

        for (GridCacheEntryEx entry : entries) {
            // Since entries were collected before locks are added, some of them may become obsolete.
            while (true) {
                try {
                    for (GridCacheMvccCandidate cand : entry.localCandidates()) {
                        if (cand.version().isLess(baseVer))
                            lessPending.add(cand.version());
                    }

                    break; // While.
                }
                catch (GridCacheEntryRemovedException ignored) {
                    if (log.isDebugEnabled())
                        log.debug("Got removed entry is localDhtPendingVersions (will retry): " + entry);

                    entry = entryExx(entry.key());
                }
            }
        }

        return lessPending;
    }

    /**
     * @param nodeId Node ID.
     * @param req Request.
     */
    private void clearLocks(UUID nodeId, GridDistributedUnlockRequest req) {
        assert nodeId != null;

        List<KeyCacheObject> keys = req.keys();

        if (keys != null) {
            for (KeyCacheObject key : keys) {
                while (true) {
                    GridDistributedCacheEntry entry = peekExx(key);

                    if (entry == null)
                        // Nothing to unlock.
                        break;

                    try {
                        entry.doneRemote(
                            req.version(),
                            req.version(),
                            null,
                            null,
                            null,
                            /*system invalidate*/false);

                        // Note that we don't reorder completed versions here,
                        // as there is no point to reorder relative to the version
                        // we are about to remove.
                        if (entry.removeLock(req.version())) {
                            if (log.isDebugEnabled())
                                log.debug("Removed lock [lockId=" + req.version() + ", key=" + key + ']');
                        }
                        else {
                            if (log.isDebugEnabled())
                                log.debug("Received unlock request for unknown candidate " +
                                    "(added to cancelled locks set): " + req);
                        }

                        entry.touch();

                        break;
                    }
                    catch (GridCacheEntryRemovedException ignored) {
                        if (log.isDebugEnabled())
                            log.debug("Received remove lock request for removed entry (will retry) [entry=" +
                                entry + ", req=" + req + ']');
                    }
                }
            }
        }
    }

    /**
     * @param nodeId Sender ID.
     * @param req Request.
     */
    private void processNearUnlockRequest(UUID nodeId, GridNearUnlockRequest req) {
        assert ctx.affinityNode();
        assert nodeId != null;

        removeLocks(nodeId, req.version(), req.keys(), true);
    }

    /**
     * @param nodeId Sender node ID.
     * @param topVer Topology version.
     * @param cached Entry.
     * @param readers Readers for this entry.
     * @param dhtMap DHT map.
     * @param nearMap Near map.
     */
    private void map(UUID nodeId,
        AffinityTopologyVersion topVer,
        GridCacheEntryEx cached,
        Collection<UUID> readers,
        Map<ClusterNode, List<KeyCacheObject>> dhtMap,
        Map<ClusterNode, List<KeyCacheObject>> nearMap
    ) {
        List<ClusterNode> dhtNodes = ctx.dht().topology().nodes(cached.partition(), topVer);

        ClusterNode primary = dhtNodes.get(0);

        assert primary != null;

        if (!primary.id().equals(ctx.nodeId())) {
            if (log.isDebugEnabled())
                log.debug("Primary node mismatch for unlock [entry=" + cached + ", expected=" + ctx.nodeId() +
                    ", actual=" + U.toShortString(primary) + ']');

            return;
        }

        if (log.isDebugEnabled())
            log.debug("Mapping entry to DHT nodes [nodes=" + U.toShortString(dhtNodes) + ", entry=" + cached + ']');

        Collection<ClusterNode> nearNodes = null;

        if (!F.isEmpty(readers)) {
            nearNodes = ctx.discovery().nodes(readers, F0.not(F.idForNodeId(nodeId)));

            if (log.isDebugEnabled())
                log.debug("Mapping entry to near nodes [nodes=" + U.toShortString(nearNodes) + ", entry=" + cached +
                    ']');
        }
        else {
            if (log.isDebugEnabled())
                log.debug("Entry has no near readers: " + cached);
        }

        map(cached, F.view(dhtNodes, F.remoteNodes(ctx.nodeId())), dhtMap); // Exclude local node.
        map(cached, nearNodes, nearMap);
    }

    /**
     * @param entry Entry.
     * @param nodes Nodes.
     * @param map Map.
     */
    private void map(GridCacheEntryEx entry,
        @Nullable Iterable<? extends ClusterNode> nodes,
        Map<ClusterNode, List<KeyCacheObject>> map) {
        if (nodes != null) {
            for (ClusterNode n : nodes) {
                List<KeyCacheObject> keys = map.get(n);

                if (keys == null)
                    map.put(n, keys = new LinkedList<>());

                keys.add(entry.key());
            }
        }
    }

    /**
     * @param nodeId Node ID.
     * @param ver Version.
     * @param keys Keys.
     * @param unmap Flag for un-mapping version.
     */
    public void removeLocks(UUID nodeId, GridCacheVersion ver, Iterable<KeyCacheObject> keys, boolean unmap) {
        assert nodeId != null;
        assert ver != null;

        if (F.isEmpty(keys))
            return;

        // Remove mapped versions.
        GridCacheVersion dhtVer = unmap ? ctx.mvcc().unmapVersion(ver) : ver;

        ctx.mvcc().addRemoved(ctx, ver);

        Map<ClusterNode, List<KeyCacheObject>> dhtMap = new HashMap<>();
        Map<ClusterNode, List<KeyCacheObject>> nearMap = new HashMap<>();

        GridCacheVersion obsoleteVer = null;

        for (KeyCacheObject key : keys) {
            while (true) {
                boolean created = false;

                GridDhtCacheEntry entry = peekExx(key);

                if (entry == null) {
                    entry = entryExx(key);

                    created = true;
                }

                try {
                    GridCacheMvccCandidate cand = null;

                    if (dhtVer == null) {
                        cand = entry.localCandidateByNearVersion(ver, true);

                        if (cand != null)
                            dhtVer = cand.version();
                        else {
                            if (log.isDebugEnabled())
                                log.debug("Failed to locate lock candidate based on dht or near versions [nodeId=" +
                                    nodeId + ", ver=" + ver + ", unmap=" + unmap + ", keys=" + keys + ']');

                            entry.removeLock(ver);

                            if (created) {
                                if (obsoleteVer == null)
                                    obsoleteVer = ctx.versions().next();

                                if (entry.markObsolete(obsoleteVer))
                                    removeEntry(entry);
                            }

                            break;
                        }
                    }

                    if (cand == null)
                        cand = entry.candidate(dhtVer);

                    AffinityTopologyVersion topVer = cand == null
                        ? AffinityTopologyVersion.NONE
                        : cand.topologyVersion();

                    // Note that we obtain readers before lock is removed.
                    // Even in case if entry would be removed just after lock is removed,
                    // we must send release messages to backups and readers.
                    Collection<UUID> readers = entry.readers();

                    // Note that we don't reorder completed versions here,
                    // as there is no point to reorder relative to the version
                    // we are about to remove.
                    if (entry.removeLock(dhtVer)) {
                        // Map to backups and near readers.
                        map(nodeId, topVer, entry, readers, dhtMap, nearMap);

                        if (log.isDebugEnabled())
                            log.debug("Removed lock [lockId=" + ver + ", key=" + key + ']');
                    }
                    else if (log.isDebugEnabled())
                        log.debug("Received unlock request for unknown candidate " +
                            "(added to cancelled locks set) [ver=" + ver + ", entry=" + entry + ']');

                    if (created && entry.markObsolete(dhtVer))
                        removeEntry(entry);

                    entry.touch();

                    break;
                }
                catch (GridCacheEntryRemovedException ignored) {
                    if (log.isDebugEnabled())
                        log.debug("Received remove lock request for removed entry (will retry): " + entry);
                }
            }
        }

        IgnitePair<Collection<GridCacheVersion>> versPair = ctx.tm().versions(ver);

        Collection<GridCacheVersion> committed = versPair.get1();
        Collection<GridCacheVersion> rolledback = versPair.get2();

        // Backups.
        for (Map.Entry<ClusterNode, List<KeyCacheObject>> entry : dhtMap.entrySet()) {
            ClusterNode n = entry.getKey();

            List<KeyCacheObject> keyBytes = entry.getValue();

            GridDhtUnlockRequest req = new GridDhtUnlockRequest(ctx.cacheId(), keyBytes.size(),
                ctx.deploymentEnabled());

            req.version(dhtVer);

            try {
                for (KeyCacheObject key : keyBytes)
                    req.addKey(key, ctx);

                keyBytes = nearMap.get(n);

                if (keyBytes != null)
                    for (KeyCacheObject key : keyBytes)
                        req.addNearKey(key);

                req.completedVersions(committed, rolledback);

                ctx.io().send(n, req, ctx.ioPolicy());
            }
            catch (ClusterTopologyCheckedException ignore) {
                if (log.isDebugEnabled())
                    log.debug("Node left while sending unlock request: " + n);
            }
            catch (IgniteCheckedException e) {
                U.error(log, "Failed to send unlock request to node (will make best effort to complete): " + n, e);
            }
        }

        // Readers.
        for (Map.Entry<ClusterNode, List<KeyCacheObject>> entry : nearMap.entrySet()) {
            ClusterNode n = entry.getKey();

            if (!dhtMap.containsKey(n)) {
                List<KeyCacheObject> keyBytes = entry.getValue();

                GridDhtUnlockRequest req = new GridDhtUnlockRequest(ctx.cacheId(), keyBytes.size(),
                    ctx.deploymentEnabled());

                req.version(dhtVer);

                try {
                    for (KeyCacheObject key : keyBytes)
                        req.addNearKey(key);

                    req.completedVersions(committed, rolledback);

                    ctx.io().send(n, req, ctx.ioPolicy());
                }
                catch (ClusterTopologyCheckedException ignore) {
                    if (log.isDebugEnabled())
                        log.debug("Node left while sending unlock request: " + n);
                }
                catch (IgniteCheckedException e) {
                    U.error(log, "Failed to send unlock request to node (will make best effort to complete): " + n, e);
                }
            }
        }
    }

    /**
     * @param key Key
     * @param ver Version.
     * @throws IgniteCheckedException If invalidate failed.
     */
    private void invalidateNearEntry(KeyCacheObject key, GridCacheVersion ver) throws IgniteCheckedException {
        GridCacheEntryEx nearEntry = near().peekEx(key);

        if (nearEntry != null)
            nearEntry.invalidate(ver);
    }

    /**
     * @param key Key
     */
    private void obsoleteNearEntry(KeyCacheObject key) {
        GridCacheEntryEx nearEntry = near().peekEx(key);

        if (nearEntry != null)
            nearEntry.markObsolete(ctx.versions().next());
    }

    /**
     * @param nodeId Node ID.
     * @param req Request.
     */
    private void processNearTxQueryResultsEnlistRequest(UUID nodeId, final GridNearTxQueryResultsEnlistRequest req) {
        assert nodeId != null;
        assert req != null;

        ClusterNode nearNode = ctx.discovery().node(nodeId);

        GridDhtTxLocal tx;

        try {
            tx = initTxTopologyVersion(nodeId,
                nearNode,
                req.version(),
                req.futureId(),
                req.miniId(),
                req.firstClientRequest(),
                req.topologyVersion(),
                req.threadId(),
                req.txTimeout(),
                req.subjectId(),
                req.taskNameHash(),
                req.mvccSnapshot());
        }
        catch (Throwable e) {
            GridNearTxQueryResultsEnlistResponse res = new GridNearTxQueryResultsEnlistResponse(req.cacheId(),
                req.futureId(),
                req.miniId(),
                req.version(),
                e);

            try {
                ctx.io().send(nearNode, res, ctx.ioPolicy());
            }
            catch (IgniteCheckedException ioEx) {
                U.error(log, "Failed to send near enlist response " +
                    "[txId=" + req.version() + ", node=" + nodeId + ", res=" + res + ']', ioEx);
            }

            if (e instanceof Error)
                throw (Error) e;

            return;
        }

        GridDhtTxQueryResultsEnlistFuture fut = new GridDhtTxQueryResultsEnlistFuture(
            nodeId,
            req.version(),
            req.mvccSnapshot(),
            req.threadId(),
            req.futureId(),
            req.miniId(),
            tx,
            req.timeout(),
            ctx,
            req.rows(),
            req.operation());

        fut.listen(NearTxQueryEnlistResultHandler.instance());

        fut.init();
    }

    /**
     * @param nodeId Node ID.
     * @param req Request.
     */
    private void processNearTxEnlistRequest(UUID nodeId, final GridNearTxEnlistRequest req) {
        assert nodeId != null;
        assert req != null;

        ClusterNode nearNode = ctx.discovery().node(nodeId);

        GridDhtTxLocal tx;

        try {
            tx = initTxTopologyVersion(nodeId,
                nearNode,
                req.version(),
                req.futureId(),
                req.miniId(),
                req.firstClientRequest(),
                req.topologyVersion(),
                req.threadId(),
                req.txTimeout(),
                req.subjectId(),
                req.taskNameHash(),
                req.mvccSnapshot());
        }
        catch (IgniteCheckedException | IgniteException ex) {
            GridNearTxEnlistResponse res = new GridNearTxEnlistResponse(req.cacheId(),
                req.futureId(),
                req.miniId(),
                req.version(),
                ex);

            try {
                ctx.io().send(nearNode, res, ctx.ioPolicy());
            }
            catch (IgniteCheckedException e) {
                U.error(log, "Failed to send near enlist response [" +
                    "txId=" + req.version() +
                    ", node=" + nodeId +
                    ", res=" + res + ']', e);
            }

            return;
        }

        GridDhtTxEnlistFuture fut = new GridDhtTxEnlistFuture(
            nodeId,
            req.version(),
            req.mvccSnapshot(),
            req.threadId(),
            req.futureId(),
            req.miniId(),
            tx,
            req.timeout(),
            ctx,
            req.rows(),
            req.operation(),
            req.filter(),
            req.needRes(),
            req.keepBinary());

        fut.listen(NearTxResultHandler.instance());

        fut.init();
    }

    /**
     * @param nodeId Near node id.
     * @param nearNode Near node.
     * @param nearLockVer Near lock version.
     * @param nearFutId Near future id.
     * @param nearMiniId Near mini-future id.
     * @param firstClientReq First client request flag.
     * @param topVer Topology version.
     * @param nearThreadId Near node thread id.
     * @param timeout Timeout.
     * @param txSubjectId Transaction subject id.
     * @param txTaskNameHash Transaction task name hash.
     * @param snapshot Mvcc snapsht.
     * @return Transaction.
     */
    public GridDhtTxLocal initTxTopologyVersion(UUID nodeId,
        ClusterNode nearNode,
        GridCacheVersion nearLockVer,
        IgniteUuid nearFutId,
        int nearMiniId,
        boolean firstClientReq,
        AffinityTopologyVersion topVer,
        long nearThreadId,
        long timeout,
        UUID txSubjectId,
        int txTaskNameHash,
        MvccSnapshot snapshot) throws IgniteException, IgniteCheckedException {

        assert ctx.affinityNode();

        if (txLockMsgLog.isDebugEnabled()) {
            txLockMsgLog.debug("Received near enlist request [txId=" + nearLockVer +
                ", node=" + nodeId + ']');
        }

        if (nearNode == null) {
            U.warn(txLockMsgLog, "Received near enlist request from unknown node (will ignore) [txId=" + nearLockVer +
                ", node=" + nodeId + ']');

            return null;
        }

        GridDhtTxLocal tx = null;

        GridCacheVersion dhtVer = ctx.tm().mappedVersion(nearLockVer);

        if (dhtVer != null)
            tx = ctx.tm().tx(dhtVer);

        GridDhtPartitionTopology top = null;

        if (tx == null) {
            if (firstClientReq) {
                assert nearNode.isClient();

                top = topology();

                top.readLock();

                GridDhtTopologyFuture topFut = top.topologyVersionFuture();

                boolean done = topFut.isDone();

                if (!done || !(topFut.topologyVersion().compareTo(topVer) >= 0
                    && ctx.shared().exchange().lastAffinityChangedTopologyVersion(topFut.initialVersion()).compareTo(topVer) <= 0)) {
                    // TODO IGNITE-7164 Wait for topology change, remap client TX in case affinity was changed.
                    top.readUnlock();

                    throw new ClusterTopologyException("Topology was changed. Please retry on stable topology.");
                }
            }

            try {
                tx = new GridDhtTxLocal(
                    ctx.shared(),
                    topVer,
                    nearNode.id(),
                    nearLockVer,
                    nearFutId,
                    nearMiniId,
                    nearThreadId,
                    false,
                    false,
                    ctx.systemTx(),
                    false,
                    ctx.ioPolicy(),
                    PESSIMISTIC,
                    REPEATABLE_READ,
                    timeout,
                    false,
                    false,
                    false,
                    -1,
                    null,
                    txSubjectId,
                    txTaskNameHash,
                    null,
                    null);

                // if (req.syncCommit())
                tx.syncMode(FULL_SYNC);

                tx = ctx.tm().onCreated(null, tx);

                if (tx == null || !tx.init()) {
                    String msg = "Failed to acquire lock (transaction has been completed): " +
                        nearLockVer;

                    U.warn(log, msg);

                    try {
                        if (tx != null)
                            tx.rollbackDhtLocal();
                    }
                    catch (IgniteCheckedException ex) {
                        U.error(log, "Failed to rollback the transaction: " + tx, ex);
                    }

                    throw new IgniteCheckedException(msg);
                }

                tx.mvccSnapshot(snapshot);
                tx.topologyVersion(topVer);
            }
            finally {
                if (top != null)
                    top.readUnlock();
            }
        }

        ctx.tm().txContext(tx);

        return tx;
    }

    /**
     * @param nodeId Node ID.
     * @param res Response.
     */
    private void processNearTxEnlistResponse(UUID nodeId, final GridNearTxEnlistResponse res) {
        GridNearTxEnlistFuture fut = (GridNearTxEnlistFuture)
            ctx.mvcc().versionedFuture(res.version(), res.futureId());

        if (fut != null)
            fut.onResult(nodeId, res);
    }

    /**
     * @param nodeId Node ID.
     * @param res Response.
     */
    private void processNearTxQueryEnlistResponse(UUID nodeId, final GridNearTxQueryEnlistResponse res) {
        GridNearTxQueryEnlistFuture fut = (GridNearTxQueryEnlistFuture)ctx.mvcc().versionedFuture(res.version(), res.futureId());

        if (fut != null)
            fut.onResult(nodeId, res);
    }

    /**
     * @param nodeId Node ID.
     * @param res Response.
     */
    private void processNearTxQueryResultsEnlistResponse(UUID nodeId, final GridNearTxQueryResultsEnlistResponse res) {
        GridNearTxQueryResultsEnlistFuture fut = (GridNearTxQueryResultsEnlistFuture)
            ctx.mvcc().versionedFuture(res.version(), res.futureId());

        if (fut != null)
            fut.onResult(nodeId, res);
    }

    /**
     * @param primary Primary node.
     * @param req Message.
     * @param first Flag if this is a first request in current operation.
     */
    private void processDhtTxQueryEnlistRequest(UUID primary, GridDhtTxQueryEnlistRequest req, boolean first) {
        try {
            assert req.version() != null && req.op() != null;

            GridDhtTxRemote tx = ctx.tm().tx(req.version());

            if (tx == null) {
                if (!first)
                    throw new IgniteCheckedException("Can not find a transaction for version [version="
                        + req.version() + ']');

                GridDhtTxQueryFirstEnlistRequest req0 = (GridDhtTxQueryFirstEnlistRequest)req;

                tx = new GridDhtTxRemote(ctx.shared(),
                    req0.nearNodeId(),
                    req0.dhtFutureId(),
                    primary,
                    req0.nearXidVersion(),
                    req0.topologyVersion(),
                    req0.version(),
                    null,
                    ctx.systemTx(),
                    ctx.ioPolicy(),
                    PESSIMISTIC,
                    REPEATABLE_READ,
                    false,
                    req0.timeout(),
                    -1,
                    req0.subjectId(),
                    req0.taskNameHash(),
                    false,
                    null);

                tx.mvccSnapshot(new MvccSnapshotWithoutTxs(req0.coordinatorVersion(), req0.counter(),
                    MVCC_OP_COUNTER_NA, req0.cleanupVersion()));

                tx = ctx.tm().onCreated(null, tx);

                if (tx == null || !ctx.tm().onStarted(tx)) {
                    throw new IgniteTxRollbackCheckedException("Failed to update backup " +
                        "(transaction has been completed): " + req0.version());
                }
            }

            assert tx != null;

            MvccSnapshot s0 = tx.mvccSnapshot();

            MvccSnapshot snapshot = new MvccSnapshotWithoutTxs(s0.coordinatorVersion(), s0.counter(),
                req.operationCounter(), s0.cleanupVersion());

            ctx.tm().txHandler().mvccEnlistBatch(tx, ctx, req.op(), req.keys(), req.values(), snapshot,
                req.dhtFutureId(), req.batchId());

            GridDhtTxQueryEnlistResponse res = new GridDhtTxQueryEnlistResponse(req.cacheId(),
                req.dhtFutureId(),
                req.batchId(),
                null);

            try {
                ctx.io().send(primary, res, ctx.ioPolicy());
            }
            catch (IgniteCheckedException ioEx) {
                U.error(log, "Failed to send DHT enlist reply to primary node [node: " + primary + ", req=" +
                    req + ']', ioEx);
            }
        }
        catch (Throwable e) {
            GridDhtTxQueryEnlistResponse res = new GridDhtTxQueryEnlistResponse(ctx.cacheId(),
                req.dhtFutureId(),
                req.batchId(),
                e);

            try {
                ctx.io().send(primary, res, ctx.ioPolicy());
            }
            catch (IgniteCheckedException ioEx) {
                U.error(log, "Failed to send DHT enlist reply to primary node " +
                    "[node: " + primary + ", req=" + req + ']', ioEx);
            }

            if (e instanceof Error)
                throw (Error) e;
        }
    }

    /**
     * @param backup Backup node.
     * @param res Response message.
     */
    private void processDhtTxQueryEnlistResponse(UUID backup, GridDhtTxQueryEnlistResponse res) {
        GridDhtTxAbstractEnlistFuture fut = (GridDhtTxAbstractEnlistFuture)
            ctx.mvcc().future(res.futureId());

        if (fut == null) {
            U.warn(log, "Received dht enlist response for unknown future [futId=" + res.futureId() +
                ", batchId=" + res.batchId() +
                ", node=" + backup + ']');

            return;
        }

        fut.onResult(backup, res);
    }

}
