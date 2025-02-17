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

package org.apache.ignite.internal.processors.query.h2.twostep;

import java.util.concurrent.atomic.AtomicReferenceArray;
import org.apache.ignite.internal.processors.cache.GridCacheContext;
import org.apache.ignite.internal.processors.query.GridQueryCancel;
import org.apache.ignite.internal.processors.query.h2.IgniteH2Indexing;
import org.apache.ignite.internal.processors.query.h2.opt.QueryContext;
import org.jetbrains.annotations.Nullable;

/**
 * Mapper query results.
 */
class MapQueryResults {
    /** H2 indexing. */
    private final IgniteH2Indexing h2;

    /** */
    private final long qryReqId;

    /** */
    private final AtomicReferenceArray<MapQueryResult> results;

    /** */
    private final GridQueryCancel[] cancels;

    /** */
    private final GridCacheContext<?, ?> cctx;

    /** Lazy mode. */
    private final boolean lazy;

    /** */
    private volatile boolean cancelled;

    /** Query context. */
    private final QueryContext qctx;

    /**
     * Constructor.
     *
     * @param h2 Indexing instance.
     * @param qryReqId Query request ID.
     * @param qrys Number of queries.
     * @param cctx Cache context.
     * @param lazy Lazy flag.
     * @param qctx Query context.
     */
    MapQueryResults(IgniteH2Indexing h2, long qryReqId, int qrys, @Nullable GridCacheContext<?, ?> cctx,
        boolean lazy, QueryContext qctx) {
        this.h2 = h2;
        this.qryReqId = qryReqId;
        this.cctx = cctx;
        this.lazy = lazy;
        this.qctx = qctx;

        results = new AtomicReferenceArray<>(qrys);
        cancels = new GridQueryCancel[qrys];

        for (int i = 0; i < cancels.length; i++)
            cancels[i] = new GridQueryCancel();
    }

    /**
     * @param qry Query result index.
     * @return Query result.
     */
    MapQueryResult result(int qry) {
        return results.get(qry);
    }

    /**
     * Get cancel token for query.
     *
     * @param qryIdx Query index.
     * @return Cancel token.
     */
    GridQueryCancel queryCancel(int qryIdx) {
        return cancels[qryIdx];
    }

    /**
     * Add result.
     * @param qryIdx Query result index.
     * @param res Result.
     */
    void addResult(int qryIdx, MapQueryResult res) {
        if (!results.compareAndSet(qryIdx, null, res))
            throw new IllegalStateException();
    }

    /**
     * @return {@code true} If all results are closed.
     */
    boolean isAllClosed() {
        for (int i = 0; i < results.length(); i++) {
            MapQueryResult res = results.get(i);

            if (res == null || !res.closed())
                return false;
        }

        return true;
    }

    /**
     * Cancels the query.
     */
    void cancel() {
        synchronized (this) {
            if (cancelled)
                return;

            cancelled = true;

            for (int i = 0; i < results.length(); i++) {
                GridQueryCancel cancel = cancels[i];

                if (cancel != null)
                    cancel.cancel();
            }
        }

        // The closing result set is synchronized by themselves.
        // Include to synchronize block may be cause deadlock on <this> and MapQueryResult#lock.
        close();
    }

    /**
     * Wrap MapQueryResult#close to synchronize close vs cancel.
     * We have do it because connection returns to pool after close ResultSet but the whole MapQuery
     * (that may contains several queries) may be canceled later.
     *
     * @param idx Map query (result) index.
     */
    void closeResult(int idx) {
        MapQueryResult res = results.get(idx);

        if (res != null && !res.closed()) {
            try {
                // Session isn't set for lazy=false queries.
                // Also session == null when result already closed.
                res.lock();
                res.lockTables();

                synchronized (this) {
                    res.close();

                    // The statement of the closed result must not be canceled
                    // because statement & connection may be reused.
                    cancels[idx] = null;
                }
            }
            finally {
                res.unlock();
            }
        }
    }

    /**
     *
     */
    public void close() {
        for (int i = 0; i < results.length(); i++)
            closeResult(i);

        if (lazy)
            releaseQueryContext();
    }

    /**
     * @return Cancel flag.
     */
    boolean cancelled() {
        return cancelled;
    }

    /**
     * @return Query request ID.
     */
    long queryRequestId() {
        return qryReqId;
    }

    /**
     * @return Query context.
     */
    public QueryContext queryContext() {
        return qctx;
    }

    /**
     * Release query context.
     */
    public void releaseQueryContext() {
        h2.queryContextRegistry().clearThreadLocal();

        if (qctx.distributedJoinContext() == null)
            qctx.clearContext(false);
    }

    /**
     * @return Lazy flag.
     */
    public boolean isLazy() {
        return lazy;
    }
}
