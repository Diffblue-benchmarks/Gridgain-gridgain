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

import java.lang.reflect.Field;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;
import org.apache.ignite.IgniteLogger;
import org.apache.ignite.events.CacheQueryReadEvent;
import org.apache.ignite.internal.GridKernalContext;
import org.apache.ignite.internal.processors.cache.GridCacheContext;
import org.apache.ignite.internal.processors.cache.query.CacheQueryType;
import org.apache.ignite.internal.processors.cache.query.GridCacheSqlQuery;
import org.apache.ignite.internal.processors.cache.tree.CacheDataTree;
import org.apache.ignite.internal.processors.query.h2.H2ConnectionWrapper;
import org.apache.ignite.internal.processors.query.h2.H2Utils;
import org.apache.ignite.internal.processors.query.h2.IgniteH2Indexing;
import org.apache.ignite.internal.processors.query.h2.ThreadLocalObjectPool;
import org.apache.ignite.internal.processors.query.h2.opt.GridH2Table;
import org.apache.ignite.internal.processors.query.h2.opt.GridH2ValueCacheObject;
import org.apache.ignite.internal.util.typedef.F;
import org.apache.ignite.internal.util.typedef.internal.U;
import org.h2.engine.Session;
import org.h2.jdbc.JdbcResultSet;
import org.h2.result.LazyResult;
import org.h2.result.ResultInterface;
import org.h2.value.Value;
import org.jetbrains.annotations.Nullable;

import static org.apache.ignite.events.EventType.EVT_CACHE_QUERY_OBJECT_READ;

/**
 * Mapper result for a single part of the query.
 */
class MapQueryResult {
    /** */
    private static final Field RESULT_FIELD;

    /*
     * Initialize.
     */
    static {
        try {
            RESULT_FIELD = JdbcResultSet.class.getDeclaredField("result");

            RESULT_FIELD.setAccessible(true);
        }
        catch (NoSuchFieldException e) {
            throw new IllegalStateException("Check H2 version in classpath.", e);
        }
    }

    /** Indexing. */
    private final IgniteH2Indexing h2;

    /** */
    private final GridCacheContext<?, ?> cctx;

    /** */
    private final GridCacheSqlQuery qry;

    /** */
    private final UUID qrySrcNodeId;

    /** */
    private volatile Result res;

    /** */
    private final IgniteLogger log;

    /** */
    private final Object[] params;

    /** */
    private int page;

    /** */
    private boolean cpNeeded;

    /** */
    private volatile boolean closed;

    /** H2 session. */
    private final Session ses;

    /** Detached connection. Used for lazy execution to prevent connection sharing. */
    private ThreadLocalObjectPool<H2ConnectionWrapper>.Reusable detachedConn;

    /** */
    private final ReentrantLock lock = new ReentrantLock();

    /**
     * @param h2 H2 indexing.
     * @param cctx Cache context.
     * @param qrySrcNodeId Query source node.
     * @param qry Query.
     * @param params Query params.
     * @param conn H2 connection wrapper.
     * @param log Logger.
     */
    MapQueryResult(IgniteH2Indexing h2, @Nullable GridCacheContext cctx,
        UUID qrySrcNodeId, GridCacheSqlQuery qry, Object[] params, H2ConnectionWrapper conn, IgniteLogger log) {
        this.h2 = h2;
        this.cctx = cctx;
        this.qry = qry;
        this.params = params;
        this.qrySrcNodeId = qrySrcNodeId;
        this.cpNeeded = F.eq(h2.kernalContext().localNodeId(), qrySrcNodeId);
        this.log = log;

        ses = H2Utils.session(conn.connection());
    }

    /** */
    void openResult(ResultSet rs) {
        res = new Result(rs);
    }

    /**
     * @return Page number.
     */
    int page() {
        return page;
    }

    /**
     * @return Row count.
     */
    int rowCount() {
        assert res != null;

        return res.rowCnt;
    }

    /**
     * @return Column ocunt.
     */
    int columnCount() {
        assert res != null;

        return res.cols;
    }

    /**
     * @return Closed flag.
     */
    boolean closed() {
        return closed;
    }

    /**
     * @param rows Collection to fetch into.
     * @param pageSize Page size.
     * @param dataPageScanEnabled If data page scan is enabled.
     * @return {@code true} If there are no more rows available.
     */
    boolean fetchNextPage(List<Value[]> rows, int pageSize, Boolean dataPageScanEnabled) {
        assert lock.isHeldByCurrentThread();

        if (closed)
            return true;

        assert res != null;

        boolean readEvt = cctx != null && cctx.name() != null && cctx.events().isRecordable(EVT_CACHE_QUERY_OBJECT_READ);

        page++;

        h2.enableDataPageScan(dataPageScanEnabled);

        try {
            for (int i = 0; i < pageSize; i++) {
                if (!res.res.next())
                    return true;

                Value[] row = res.res.currentRow();

                if (cpNeeded) {
                    boolean copied = false;

                    for (int j = 0; j < row.length; j++) {
                        Value val = row[j];

                        if (val instanceof GridH2ValueCacheObject) {
                            GridH2ValueCacheObject valCacheObj = (GridH2ValueCacheObject)val;

                            row[j] = new GridH2ValueCacheObject(valCacheObj.getCacheObject(), h2.objectContext()) {
                                @Override public Object getObject() {
                                    return getObject(true);
                                }
                            };

                            copied = true;
                        }
                    }

                    if (i == 0 && !copied)
                        cpNeeded = false; // No copy on read caches, skip next checks.
                }

                assert row != null;

                if (readEvt) {
                    GridKernalContext ctx = h2.kernalContext();

                    ctx.event().record(new CacheQueryReadEvent<>(
                        ctx.discovery().localNode(),
                        "SQL fields query result set row read.",
                        EVT_CACHE_QUERY_OBJECT_READ,
                        CacheQueryType.SQL.name(),
                        cctx.name(),
                        null,
                        qry.query(),
                        null,
                        null,
                        params,
                        qrySrcNodeId,
                        null,
                        null,
                        null,
                        null,
                        row(row)));
                }

                rows.add(res.res.currentRow());
            }

            if (detachedConn == null && res.res.hasNext())
                detachedConn = h2.connections().detachThreadConnection();

            return !res.res.hasNext();
        }
        finally {
            CacheDataTree.setDataPageScanEnabled(false);
        }
    }

    /**
     * @param row Values array row.
     * @return Objects list row.
     */
    private List<?> row(Value[] row) {
        List<Object> res = new ArrayList<>(row.length);

        for (Value v : row)
            res.add(v.getObject());

        return res;
    }

    /**
     * Close the result.
     */
    void close() {
        assert lock.isHeldByCurrentThread();

        if (closed)
            return;

        closed = true;

        if (res != null)
            res.close();

        if (detachedConn != null)
            detachedConn.recycle();

        detachedConn = null;
    }

    /** */
    public void lock() {
        if (!lock.isHeldByCurrentThread())
            lock.lock();
    }

    /** */
    public void lockTables() {
        if (ses.isLazyQueryExecution() && !closed)
            GridH2Table.readLockTables(ses);
    }

    /** */
    public void unlock() {
        if (lock.isHeldByCurrentThread())
            lock.unlock();
    }

    /** */
    public void unlockTables() {
        if (ses.isLazyQueryExecution())
            GridH2Table.unlockTables(ses);
    }

    /**
     *
     */
    public void checkTablesVersions() {
        if (ses.isLazyQueryExecution())
            GridH2Table.checkTablesVersions(ses);
    }

    /** */
    private class Result {
        /** */
        private final ResultInterface res;

        /** */
        private final ResultSet rs;

        /** */
        private final int cols;

        /** */
        private final int rowCnt;

        /**
         * Constructor.
         *
         * @param rs H2 result set.
         */
        Result(ResultSet rs) {
            if (rs != null) {
                this.rs = rs;

                try {
                    res = (ResultInterface)RESULT_FIELD.get(rs);
                }
                catch (IllegalAccessException e) {
                    throw new IllegalStateException(e); // Must not happen.
                }

                rowCnt = (res instanceof LazyResult) ? -1 : res.getRowCount();
                cols = res.getVisibleColumnCount();
            }
            else {
                this.rs = null;
                this.res = null;
                this.cols = -1;
                this.rowCnt = -1;

                closed = true;
            }
        }

        /** */
        void close() {
            U.close(rs, log);
        }
    }
}
