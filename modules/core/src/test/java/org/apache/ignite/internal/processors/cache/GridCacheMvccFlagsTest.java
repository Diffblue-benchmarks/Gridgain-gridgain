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

import java.util.UUID;
import org.apache.ignite.configuration.CacheConfiguration;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.internal.IgniteKernal;
import org.apache.ignite.internal.processors.cache.version.GridCacheVersion;
import org.apache.ignite.testframework.junits.common.GridCommonAbstractTest;
import org.junit.Test;

import static org.apache.ignite.cache.CacheMode.REPLICATED;

/**
 * Tests flags correctness.
 */
public class GridCacheMvccFlagsTest extends GridCommonAbstractTest {
    /** Grid. */
    private IgniteKernal grid;

    /**
     *
     */
    public GridCacheMvccFlagsTest() {
        super(true /*start grid. */);
    }

    /** {@inheritDoc} */
    @Override protected void beforeTest() throws Exception {
        grid = (IgniteKernal)grid();
    }

    /** {@inheritDoc} */
    @Override protected void afterTest() throws Exception {
        grid = null;
    }

    /** {@inheritDoc} */
    @Override protected IgniteConfiguration getConfiguration() throws Exception {
        IgniteConfiguration cfg = super.getConfiguration();

        CacheConfiguration cacheCfg = defaultCacheConfiguration();

        cacheCfg.setCacheMode(REPLICATED);

        cfg.setCacheConfiguration(cacheCfg);

        return cfg;
    }

    /**
     *
     */
    @Test
    public void testAllTrueFlags() {
        GridCacheAdapter<String, String> cache = grid.internalCache(DEFAULT_CACHE_NAME);

        GridCacheTestEntryEx entry = new GridCacheTestEntryEx(cache.context(), "1");

        UUID id = UUID.randomUUID();

        GridCacheVersion ver = new GridCacheVersion(1, 0, 0, 0);

        GridCacheMvccCandidate c = new GridCacheMvccCandidate(
            entry,
            id,
            id,
            ver,
            1,
            ver,
            true,
            true,
            true,
            true,
            true,
            true,
            null,
            false
        );

        c.setOwner();
        c.setReady();
        c.setUsed();

        short flags = c.flags();

        info("Candidate: " + c);

        for (GridCacheMvccCandidate.Mask mask : GridCacheMvccCandidate.Mask.values())
            assertTrue("Candidate: " + c, mask.get(flags));
    }

    /**
     *
     */
    @Test
    public void testAllFalseFlags() {
        GridCacheAdapter<String, String> cache = grid.internalCache(DEFAULT_CACHE_NAME);

        GridCacheTestEntryEx entry = new GridCacheTestEntryEx(cache.context(), "1");

        UUID id = UUID.randomUUID();

        GridCacheVersion ver = new GridCacheVersion(1, 0, 0, 0);

        GridCacheMvccCandidate c = new GridCacheMvccCandidate(
            entry,
            id,
            id,
            ver,
            1,
            ver,
            false,
            false,
            false,
            false,
            false,
            false,
            null,
            false
        );

        short flags = c.flags();

        info("Candidate: " + c);

        for (GridCacheMvccCandidate.Mask mask : GridCacheMvccCandidate.Mask.values())
            assertFalse("Mask check failed [mask=" + mask + ", c=" + c + ']', mask.get(flags));
    }
}
