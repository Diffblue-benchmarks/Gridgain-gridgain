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

package org.apache.ignite.internal.processors.cache.index;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.cache.query.SqlFieldsQuery;
import org.apache.ignite.configuration.CacheConfiguration;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.internal.processors.query.h2.IgniteH2Indexing;
import org.apache.ignite.internal.util.lang.GridAbsPredicate;
import org.apache.ignite.internal.util.typedef.internal.U;
import org.apache.ignite.testframework.GridTestUtils;
import org.junit.Test;

/**
 * Test for leaks JdbcConnection on SqlFieldsQuery execute.
 */
public class H2ConnectionLeaksSelfTest extends AbstractIndexingCommonTest {
    /** Cache name. */
    private static final String CACHE_NAME = "cache";

    /** Nodes count. */
    private static final int NODE_CNT = 2;

    /** Iterations count. */
    private static final int ITERS = 10;

    /** Keys count. */
    private static final int KEY_CNT = 100;

    /** Threads count. */
    private static final int THREAD_CNT = 100;

    /** {@inheritDoc} */
    @Override protected void afterTest() throws Exception {
        stopAllGrids();

        super.afterTest();
    }

    /** {@inheritDoc} */
    @Override protected IgniteConfiguration getConfiguration(String igniteInstanceName) throws Exception {
        CacheConfiguration<Long, String> ccfg = new CacheConfiguration<Long, String>().setName(CACHE_NAME)
            .setIndexedTypes(Long.class, String.class);

        IgniteConfiguration cfg = super.getConfiguration(igniteInstanceName);

        cfg.setCacheConfiguration(ccfg);

        if (getTestIgniteInstanceIndex(igniteInstanceName) != 0)
            cfg.setClientMode(true);

        return cfg;
    }

    /**
     * @throws Exception On failed.
     */
    @Test
    public void testConnectionLeaks() throws Exception {
        startGridAndPopulateCache(NODE_CNT);

        final IgniteCache cache = grid(1).cache(CACHE_NAME);

        final CountDownLatch latch = new CountDownLatch(THREAD_CNT);

        for (int i = 0; i < THREAD_CNT; i++) {
            new Thread() {
                @Override public void run() {
                    SqlFieldsQuery qry = new SqlFieldsQuery("select * from String").setLocal(false);

                    cache.query(qry).getAll();

                    latch.countDown();
                }
            }.start();
        }

        latch.await();

        checkConnectionLeaks();
    }

    /**
     * @throws Exception On failed.
     */
    @Test
    public void testConnectionLeaksOnSqlException() throws Exception {
        startGridAndPopulateCache(NODE_CNT);

        final CountDownLatch latch = new CountDownLatch(THREAD_CNT);
        final CountDownLatch latch2 = new CountDownLatch(1);

        for (int i = 0; i < THREAD_CNT; i++) {
            new Thread() {
                @Override public void run() {
                    try {
                        IgniteH2Indexing idx = (IgniteH2Indexing)grid(1).context().query().getIndexing();

                        idx.connections().executeStatement(CACHE_NAME, "select *");
                    }
                    catch (Exception e) {
                        // No-op.
                    }

                    latch.countDown();

                    try {
                        latch2.await();
                    }
                    catch (InterruptedException e) {
                        // No-op;
                    }
                }
            }.start();
        }

        try {
            latch.await();

            checkConnectionLeaks();
        }
        finally {
            latch2.countDown();
        }
    }

    /**
     * @throws Exception On failed.
     */
    @Test
    public void testDetachedConnectionOfLocalQueryOnNodeRestart() throws Exception {
        for (int i = 0; i < ITERS; ++i) {
            startGridAndPopulateCache(1);

            IgniteCache cache = grid(0).cache(CACHE_NAME);

            // Execute unfinished & finished queries.
            cache.query(new SqlFieldsQuery("select * from String").setLocal(true)).iterator().next();
            cache.query(new SqlFieldsQuery("select * from String").setLocal(true)).getAll();
            cache.query(new SqlFieldsQuery("select * from String").setLocal(true)).iterator().next();

            stopAllGrids();

            U.sleep(50);
        }

        stopAllGrids();

        checkAllConnectionAreClosed();
    }

    /**
     * @throws Exception On error.
     */
    private void checkConnectionLeaks() throws Exception {
        boolean notLeak = GridTestUtils.waitForCondition(new GridAbsPredicate() {
            @Override public boolean apply() {
                for (int i = 0; i < NODE_CNT; i++) {
                    Map<Thread, ?> conns = perThreadConnections(i);

                    for(Thread t : conns.keySet()) {
                        if (!t.isAlive())
                            return false;
                    }
                }

                return true;
            }
        }, 5000);

        if (!notLeak) {
            for (int i = 0; i < NODE_CNT; i++) {
                Map<Thread, ?> conns = perThreadConnections(i);

                for(Thread t : conns.keySet())
                    log.error("Connection is not closed for thread: " + t.getName());
            }

            fail("H2 JDBC connections leak detected. See the log above.");
        }
    }

    /**
     * @param nodeIdx Node index.
     * @return Per-thread connections.
     */
    private Map<Thread, ?> perThreadConnections(int nodeIdx) {
        return ((IgniteH2Indexing)grid(nodeIdx).context().query().getIndexing()).connections().connectionsForThread();
    }

    /**
     * @param nodes Nodes count.
     * @throws Exception On error.
     */
    private void startGridAndPopulateCache(int nodes) throws Exception {
        startGrids(NODE_CNT);

        IgniteCache<Long, String> cache = grid(0).cache(CACHE_NAME);

        for (int i = 0; i < KEY_CNT; i++)
            cache.put((long)i, String.valueOf(i));

    }
}
