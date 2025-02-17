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

package org.apache.ignite.testsuites;

import java.util.HashSet;
import java.util.List;
import org.apache.ignite.GridCacheAffinityBackupsSelfTest;
import org.apache.ignite.IgniteCacheAffinitySelfTest;
import org.apache.ignite.IgniteSystemProperties;
import org.apache.ignite.cache.affinity.AffinityClientNodeSelfTest;
import org.apache.ignite.cache.affinity.AffinityDistributionLoggingTest;
import org.apache.ignite.cache.affinity.AffinityHistoryCleanupTest;
import org.apache.ignite.cache.affinity.local.LocalAffinityFunctionTest;
import org.apache.ignite.internal.GridCachePartitionExchangeManagerHistSizeTest;
import org.apache.ignite.internal.processors.cache.CacheSerializableTransactionsTest;
import org.apache.ignite.internal.processors.cache.ClusterReadOnlyModeTest;
import org.apache.ignite.internal.processors.cache.ClusterStatePartitionedSelfTest;
import org.apache.ignite.internal.processors.cache.ClusterStateReplicatedSelfTest;
import org.apache.ignite.internal.processors.cache.ConcurrentCacheStartTest;
import org.apache.ignite.internal.processors.cache.EntryVersionConsistencyReadThroughTest;
import org.apache.ignite.internal.processors.cache.IgniteCachePutStackOverflowSelfTest;
import org.apache.ignite.internal.processors.cache.IgniteCacheStoreCollectionTest;
import org.apache.ignite.internal.processors.cache.PartitionsExchangeOnDiscoveryHistoryOverflowTest;
import org.apache.ignite.internal.processors.cache.distributed.CacheLateAffinityAssignmentNodeJoinValidationTest;
import org.apache.ignite.internal.processors.cache.distributed.IgniteCacheTxIteratorSelfTest;
import org.apache.ignite.internal.processors.cache.distributed.dht.NotMappedPartitionInTxTest;
import org.apache.ignite.internal.processors.cache.distributed.dht.atomic.IgniteCacheAtomicProtocolTest;
import org.apache.ignite.internal.processors.cache.distributed.rebalancing.CacheManualRebalancingTest;
import org.apache.ignite.internal.processors.cache.distributed.replicated.IgniteCacheSyncRebalanceModeSelfTest;
import org.apache.ignite.internal.processors.cache.store.IgniteCacheWriteBehindNoUpdateSelfTest;
import org.apache.ignite.testframework.junits.DynamicSuite;
import org.junit.runner.RunWith;

/**
 * Test suite.
 */
@RunWith(DynamicSuite.class)
public class IgniteCacheMvccTestSuite5 {
    /**
     * @return IgniteCache test suite.
     */
    public static List<Class<?>> suite() {
        System.setProperty(IgniteSystemProperties.IGNITE_FORCE_MVCC_MODE_IN_TESTS, "true");

        HashSet<Class> ignoredTests = new HashSet<>(128);

        // Skip classes that already contains Mvcc tests
        ignoredTests.add(IgniteCacheStoreCollectionTest.class);
        ignoredTests.add(EntryVersionConsistencyReadThroughTest.class);
        ignoredTests.add(ClusterReadOnlyModeTest.class);
        ignoredTests.add(NotMappedPartitionInTxTest.class);
        ignoredTests.add(IgniteCacheTxIteratorSelfTest.class);

        // Irrelevant Tx tests.
        ignoredTests.add(CacheSerializableTransactionsTest.class);
        ignoredTests.add(IgniteCachePutStackOverflowSelfTest.class);
        ignoredTests.add(IgniteCacheAtomicProtocolTest.class);

        // Other non-tx tests.
        ignoredTests.add(CacheLateAffinityAssignmentNodeJoinValidationTest.class);
        ignoredTests.add(IgniteCacheWriteBehindNoUpdateSelfTest.class);
        ignoredTests.add(IgniteCacheSyncRebalanceModeSelfTest.class);
        ignoredTests.add(ClusterStatePartitionedSelfTest.class);
        ignoredTests.add(ClusterStateReplicatedSelfTest.class);
        ignoredTests.add(CacheManualRebalancingTest.class);
        ignoredTests.add(GridCacheAffinityBackupsSelfTest.class);
        ignoredTests.add(IgniteCacheAffinitySelfTest.class);
        ignoredTests.add(AffinityClientNodeSelfTest.class);
        ignoredTests.add(LocalAffinityFunctionTest.class);
        ignoredTests.add(AffinityHistoryCleanupTest.class);
        ignoredTests.add(AffinityDistributionLoggingTest.class);
        ignoredTests.add(PartitionsExchangeOnDiscoveryHistoryOverflowTest.class);
        ignoredTests.add(GridCachePartitionExchangeManagerHistSizeTest.class);
        ignoredTests.add(ConcurrentCacheStartTest.class);

        return IgniteCacheTestSuite5.suite(ignoredTests);
    }
}
