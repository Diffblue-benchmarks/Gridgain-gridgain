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

package org.apache.ignite.internal.processors.query;

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.cache.CacheMode;
import org.apache.ignite.cache.QueryEntity;
import org.apache.ignite.cache.QueryIndex;
import org.apache.ignite.cache.affinity.rendezvous.RendezvousAffinityFunction;
import org.apache.ignite.cache.query.FieldsQueryCursor;
import org.apache.ignite.cache.query.SqlFieldsQuery;
import org.apache.ignite.configuration.CacheConfiguration;
import org.apache.ignite.internal.processors.cache.index.AbstractIndexingCommonTest;
import org.apache.ignite.internal.util.GridDebug;
import org.apache.ignite.internal.util.typedef.internal.U;
import org.apache.ignite.testframework.GridTestUtils;
import org.h2.result.LazyResult;
import org.h2.result.ResultInterface;
import org.junit.Test;

/**
 * Tests for local query execution in lazy mode.
 */
public class HashJoinQueryTest extends AbstractIndexingCommonTest {
    /** Keys counts at the RIGHT table. */
    private static final int RIGHT_CNT = 1000;

    /** Keys counts at the LEFT table. */
    private static final int LEFT_CNT = RIGHT_CNT * 500;

    private static boolean enforceJoinOrder;

    /** {@inheritDoc} */
    @SuppressWarnings("unchecked")
    @Override protected void beforeTest() throws Exception {
        super.beforeTest();

        startGrids(1);

        IgniteCache<Long, Long> cacheA = grid(0).createCache(new CacheConfiguration<Long, Long>()
            .setName("A")
            .setSqlSchema("TEST")
            .setQueryEntities(Collections.singleton(new QueryEntity(Long.class, Long.class)
                .setTableName("A")
                .addQueryField("ID", Long.class.getName(), null)
                .addQueryField("JID", Long.class.getName(), null)
                .setKeyFieldName("ID")
                .setValueFieldName("JID")
            )));

        IgniteCache cacheB = grid(0).createCache(new CacheConfiguration()
            .setCacheMode(CacheMode.REPLICATED)
            .setName("B")
            .setSqlSchema("TEST")
            .setQueryEntities(Collections.singleton(new QueryEntity(Long.class.getName(), "B_VAL")
                .setTableName("B")
                .addQueryField("ID", Long.class.getName(), null)
                .addQueryField("A_JID", Long.class.getName(), null)
                .addQueryField("VAL0", String.class.getName(), null)
                .setKeyFieldName("ID")
                .setIndexes(Collections.singleton(new QueryIndex("A_JID")))
            )));

        IgniteCache cacheC = grid(0).createCache(new CacheConfiguration()
            .setCacheMode(CacheMode.REPLICATED)
            .setName("C")
            .setSqlSchema("TEST")
            .setQueryEntities(Collections.singleton(new QueryEntity(Long.class.getName(), "C_VAL")
                .setTableName("C")
                .addQueryField("ID", Long.class.getName(), null)
                .addQueryField("A_JID", Long.class.getName(), null)
                .addQueryField("VAL0", String.class.getName(), null)
                .setKeyFieldName("ID")
                .setIndexes(Collections.singleton(new QueryIndex("A_JID")))
            )));


        Map<Long, Long> batch = new HashMap<>();
        for (long i = 0; i < LEFT_CNT; ++i) {
            batch.put(i, i % RIGHT_CNT);

            if (batch.size() > 1000) {
                cacheA.putAll(batch);

                batch.clear();
            }
        }
        if (batch.size() > 0) {
            cacheA.putAll(batch);

            batch.clear();
        }

        for (long i = 0; i < RIGHT_CNT; ++i)
            cacheB.put(i, grid(0).binary().builder("B_VAL")
                .setField("A_JID", i)
                .setField("VAL0", "val" + i)
                .build());

        for (long i = 0; i < RIGHT_CNT; ++i)
            cacheC.put(i, grid(0).binary().builder("C_VAL")
                .setField("A_JID", i)
                .setField("VAL0", "val" + i)
                .build());

        log.info("+++ FILL OK");

    }

    /** {@inheritDoc} */
    @Override protected void afterTest() throws Exception {
        stopAllGrids();

        super.afterTest();
    }

    /**
     * Test local query execution.
     */
    @Test
    public void testJoinTwo() {
        int cnt = 0;

        while (true) {
            enforceJoinOrder = true;
            long t0 = U.currentTimeMillis();

            for (int i = 0; i < 1; ++i)
                run("SELECT * FROM A, B USE INDEX(HASH_JOIN) " +
                    "WHERE A.JID = B.A_JID");

            enforceJoinOrder = false;
            long t1 = U.currentTimeMillis();

            for (int i = 0; i < 1; ++i)
                run("SELECT * FROM A, B " +
                    "WHERE A.JID = B.A_JID");

            log.info("+++ HASH=" + (t1-t0) + ", LOOP=" + (U.currentTimeMillis() - t1));

            if (cnt % 10 == 0)
                GridDebug.dumpHeap(String.format("hashj%03d.hprof", cnt / 10), true);

            cnt++;
        }
    }

    /**
     * Test local query execution.
     */
    @Test
    public void testJoinThree() {
//        enforceJoinOrder = true;
//        run("SELECT * FROM A, B USE INDEX(HASH_JOIN), C USE INDEX(HASH_JOIN) " +
//            "WHERE A.JID = B.A_JID AND A.JID=C.A_JID");

        int cnt = 0;

        while (true) {
            enforceJoinOrder = true;
            long t0 = U.currentTimeMillis();

            for (int i = 0; i < 1; ++i)
                run("SELECT * FROM A, B USE INDEX(HASH_JOIN), C USE INDEX(HASH_JOIN) " +
//                    "WHERE A.JID = B.ID AND A.JID=C.ID");
            "WHERE A.JID = B.A_JID AND A.JID=C.A_JID");

            enforceJoinOrder = false;
            long t1 = U.currentTimeMillis();
            log.info("+++ HASH=" + (t1-t0));

            for (int i = 0; i < 1; ++i)
                run("SELECT * FROM A, B, C " +
//                    "WHERE A.JID = B.ID AND A.JID=C.ID");
            "WHERE A.JID = B.A_JID AND A.JID=C.A_JID");

            log.info("+++ HASH=" + (t1-t0) + ", LOOP=" + (U.currentTimeMillis() - t1));

            if (cnt % 10 == 0)
                GridDebug.dumpHeap(String.format("hashj%03d.hprof", cnt / 10), true);

            cnt++;
        }
    }

    public void run(String sql) {
        Iterator it = sql(sql).iterator();

        while (it.hasNext())
            it.next();
    }

    /**
     * @param sql SQL query.
     * @param args Query parameters.
     * @return Results cursor.
     */
    private FieldsQueryCursor<List<?>> sql(String sql, Object ... args) {
        return grid(0).context().query().querySqlFields(new SqlFieldsQuery(sql)
            .setSchema("TEST")
            .setLazy(true)
            .setEnforceJoinOrder(enforceJoinOrder)
            .setArgs(args), false);
    }
}
