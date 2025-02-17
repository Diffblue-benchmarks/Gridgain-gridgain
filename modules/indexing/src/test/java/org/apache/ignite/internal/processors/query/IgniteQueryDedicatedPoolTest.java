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

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;
import javax.cache.Cache;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.cache.query.QueryCursor;
import org.apache.ignite.cache.query.ScanQuery;
import org.apache.ignite.cache.query.SpiQuery;
import org.apache.ignite.cache.query.SqlFieldsQuery;
import org.apache.ignite.cache.query.annotations.QuerySqlFunction;
import org.apache.ignite.configuration.CacheConfiguration;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.internal.managers.communication.GridIoManager;
import org.apache.ignite.internal.managers.communication.GridIoPolicy;
import org.apache.ignite.internal.processors.cache.CacheEntryImpl;
import org.apache.ignite.internal.util.typedef.F;
import org.apache.ignite.lang.IgniteBiPredicate;
import org.apache.ignite.spi.IgniteSpiAdapter;
import org.apache.ignite.spi.indexing.IndexingQueryFilter;
import org.apache.ignite.spi.indexing.IndexingSpi;
import org.apache.ignite.testframework.junits.common.GridCommonAbstractTest;
import org.jetbrains.annotations.Nullable;
import org.junit.Test;

/**
 * Ensures that SQL queries are executed in a dedicated thread pool.
 */
public class IgniteQueryDedicatedPoolTest extends GridCommonAbstractTest {
    /** Name of the cache for test */
    private static final String CACHE_NAME = "query_pool_test";

    /** {@inheritDoc} */
    @Override protected void beforeTest() throws Exception {
        super.beforeTest();

        startGrid("server");
    }

    /** {@inheritDoc} */
    @Override protected IgniteConfiguration getConfiguration(String gridName) throws Exception {
        IgniteConfiguration cfg = super.getConfiguration(gridName);

        CacheConfiguration<Integer, Integer> ccfg = new CacheConfiguration<>(DEFAULT_CACHE_NAME);

        ccfg.setIndexedTypes(Integer.class, Integer.class);
        ccfg.setIndexedTypes(Byte.class, Byte.class);
        ccfg.setSqlFunctionClasses(IgniteQueryDedicatedPoolTest.class);
        ccfg.setName(CACHE_NAME);

        cfg.setCacheConfiguration(ccfg);

        if ("client".equals(gridName))
            cfg.setClientMode(true);

        cfg.setIndexingSpi(new TestIndexingSpi());

        return cfg;
    }

    /** {@inheritDoc} */
    @Override protected void afterTest() throws Exception {
        super.afterTest();

        stopAllGrids();
    }

    /**
     * Tests that SQL queries involving actual network IO are executed in dedicated pool.
     * @throws Exception If failed.
     */
    @Test
    public void testSqlQueryUsesDedicatedThreadPool() throws Exception {
        try (Ignite client = startGrid("client")) {
            IgniteCache<Integer, Integer> cache = client.cache(CACHE_NAME);

            // We do this in order to have 1 row in results of select - function is called once per each row of result.
            cache.put(1, 1);

            // We have to refer to a cache explicitly in the query in order for it to be executed
            // in non local distributed manner (yes, there's a "local distributed" manner too - see link above...)
            QueryCursor<List<?>> cursor = cache.query(new SqlFieldsQuery("select currentPolicy() from Integer"));

            List<List<?>> result = cursor.getAll();

            cursor.close();

            assertEquals(1, result.size());

            Byte plc = (Byte)result.get(0).get(0);

            assertNotNull(plc);
            assertEquals(GridIoPolicy.QUERY_POOL, (byte)plc);
        }
    }

    /**
     * Tests that Scan queries are executed in dedicated pool
     * @throws Exception If failed.
     */
    @Test
    public void testScanQueryUsesDedicatedThreadPool() throws Exception {
        try (Ignite client = startGrid("client")) {
            IgniteCache<Integer, Integer> cache = client.cache(CACHE_NAME);

            cache.put(0, 0);

            QueryCursor<Cache.Entry<Object, Object>> cursor = cache.query(
                new ScanQuery<>(new IgniteBiPredicate<Object, Object>() {
                    @Override public boolean apply(Object o, Object o2) {
                        return F.eq(GridIoManager.currentPolicy(), GridIoPolicy.QUERY_POOL);
                    }
                }));

            assertEquals(1, cursor.getAll().size());

            cursor.close();
        }
    }

    /**
     * Tests that SPI queries are executed in dedicated pool
     * @throws Exception If failed.
     */
    @Test
    public void testSpiQueryUsesDedicatedThreadPool() throws Exception {
        try (Ignite client = startGrid("client")) {
            IgniteCache<Byte, Byte> cache = client.cache(CACHE_NAME);

            for (byte b = 0; b < Byte.MAX_VALUE; ++b)
                cache.put(b, b);

            QueryCursor<Cache.Entry<Byte, Byte>> cursor = cache.query(new SpiQuery<Byte, Byte>());

            List<Cache.Entry<Byte, Byte>> all = cursor.getAll();

            assertEquals(1, all.size());
            assertEquals(GridIoPolicy.QUERY_POOL, (byte)all.get(0).getValue());

            cursor.close();
        }
    }

    /**
     * Custom SQL function to return current thread name from inside query executor
     * @return Current IO policy
     */
    @SuppressWarnings("unused")
    @QuerySqlFunction(alias = "currentPolicy")
    public static Byte currentPolicy() {
         return GridIoManager.currentPolicy();
    }

    /**
     * Indexing Spi implementation for test
     */
    private static class TestIndexingSpi extends IgniteSpiAdapter implements IndexingSpi {
        /** Index. */
        private final SortedMap<Object, Object> idx = new TreeMap<>();

        /** {@inheritDoc} */
        @Override public void spiStart(@Nullable String gridName) {
            // No-op.
        }

        /** {@inheritDoc} */
        @Override public void spiStop() {
            // No-op.
        }

        /** {@inheritDoc} */
        @Override public Iterator<Cache.Entry<?, ?>> query(@Nullable String cacheName, Collection<Object> params,
            @Nullable IndexingQueryFilter filters) {
            return idx.containsKey(GridIoPolicy.QUERY_POOL) ?
                Collections.<Cache.Entry<?, ?>>singletonList(
                    new CacheEntryImpl<>(GridIoPolicy.QUERY_POOL, GridIoPolicy.QUERY_POOL)).iterator()
                : Collections.<Cache.Entry<?, ?>>emptyList().iterator();
        }

        /** {@inheritDoc} */
        @Override public void store(@Nullable String cacheName, Object key, Object val, long expirationTime) {
            idx.put(key, val);
        }

        /** {@inheritDoc} */
        @Override public void remove(@Nullable String cacheName, Object key) {
            // No-op.
        }
    }
}
