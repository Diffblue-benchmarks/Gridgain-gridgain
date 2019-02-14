/*
 *                   GridGain Community Edition Licensing
 *                   Copyright 2019 GridGain Systems, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License") modified with Commons Clause
 * Restriction; you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * Commons Clause Restriction
 *
 * The Software is provided to you by the Licensor under the License, as defined below, subject to
 * the following condition.
 *
 * Without limiting other conditions in the License, the grant of rights under the License will not
 * include, and the License does not grant to you, the right to Sell the Software.
 * For purposes of the foregoing, “Sell” means practicing any or all of the rights granted to you
 * under the License to provide to third parties, for a fee or other consideration (including without
 * limitation fees for hosting or consulting/ support services related to the Software), a product or
 * service whose value derives, entirely or substantially, from the functionality of the Software.
 * Any license notice or attribution required by the License must also include this Commons Clause
 * License Condition notice.
 *
 * For purposes of the clause above, the “Licensor” is Copyright 2019 GridGain Systems, Inc.,
 * the “License” is the Apache License, Version 2.0, and the Software is the GridGain Community
 * Edition software provided with this notice.
 */

package org.apache.ignite.internal.processors.cache.local;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.cache.query.QueryCursor;
import org.apache.ignite.cache.query.SqlFieldsQuery;
import org.apache.ignite.configuration.CacheConfiguration;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.cache.query.QueryCancelledException;
import org.apache.ignite.internal.util.typedef.G;
import org.apache.ignite.testframework.junits.common.GridCommonAbstractTest;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static org.apache.ignite.cache.CacheMode.LOCAL;

/**
 * Tests local query cancellations and timeouts.
 */
@RunWith(JUnit4.class)
public class IgniteCacheLocalQueryCancelOrTimeoutSelfTest extends GridCommonAbstractTest {
    /** Cache size. */
    private static final int CACHE_SIZE = 10_000;

    /** */
    private static final String QUERY = "select a._val, b._val from String a, String b";

    /** {@inheritDoc} */
    @Override protected IgniteConfiguration getConfiguration(String igniteInstanceName) throws Exception {
        IgniteConfiguration cfg = super.getConfiguration(igniteInstanceName);

        CacheConfiguration<Integer, String> ccfg = new CacheConfiguration<>(DEFAULT_CACHE_NAME);
        ccfg.setIndexedTypes(Integer.class, String.class);
        ccfg.setCacheMode(LOCAL);

        cfg.setCacheConfiguration(ccfg);

        return cfg;
    }

    /** {@inheritDoc} */
    @Override protected void beforeTestsStarted() throws Exception {
        super.beforeTestsStarted();

        startGrid(0);
    }

    /** {@inheritDoc} */
    @Override protected void afterTest() throws Exception {
        super.afterTest();

        for (Ignite g : G.allGrids())
            g.cache(DEFAULT_CACHE_NAME).removeAll();
    }

    /**
     * @param cache Cache.
     */
    private void loadCache(IgniteCache<Integer, String> cache) {
        int p = 1;

        for (int i = 1; i <= CACHE_SIZE; i++) {
            char[] tmp = new char[256];
            Arrays.fill(tmp, ' ');
            cache.put(i, new String(tmp));

            if (i / (float)CACHE_SIZE >= p / 10f) {
                log().info("Loaded " + i + " of " + CACHE_SIZE);

                p++;
            }
        }
    }

    /**
     * Tests cancellation.
     */
    @Test
    public void testQueryCancel() {
        testQuery(false, 1, TimeUnit.SECONDS);
    }

    /**
     * Tests cancellation with zero timeout.
     */
    @Test
    public void testQueryCancelZeroTimeout() {
        testQuery(false, 1, TimeUnit.MILLISECONDS);
    }

    /**
     * Tests timeout.
     */
    @Test
    public void testQueryTimeout() {
        testQuery(true, 1, TimeUnit.SECONDS);
    }

    /**
     * Tests cancellation.
     */
    private void testQuery(boolean timeout, int timeoutUnits, TimeUnit timeUnit) {
        Ignite ignite = grid(0);

        IgniteCache<Integer, String> cache = ignite.cache(DEFAULT_CACHE_NAME);

        loadCache(cache);

        SqlFieldsQuery qry = new SqlFieldsQuery(QUERY);

        final QueryCursor<List<?>> cursor;
        if (timeout) {
            qry.setTimeout(timeoutUnits, timeUnit);

            cursor = cache.query(qry);
        } else {
            cursor = cache.query(qry);

            ignite.scheduler().runLocal(new Runnable() {
                @Override public void run() {
                    cursor.close();
                }
            }, timeoutUnits, timeUnit);
        }

        try(QueryCursor<List<?>> ignored = cursor) {
            cursor.iterator();

            fail("Expecting timeout");
        }
        catch (Exception e) {
            assertTrue("Must throw correct exception", e.getCause() instanceof QueryCancelledException);
        }

        // Test must exit gracefully.
    }
}
