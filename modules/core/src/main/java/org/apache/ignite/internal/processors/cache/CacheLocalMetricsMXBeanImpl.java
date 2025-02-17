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

import java.util.Collections;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.IgniteCheckedException;
import org.apache.ignite.mxbean.CacheMetricsMXBean;

/**
 * Management bean that provides access to {@link IgniteCache IgniteCache}.
 */
class CacheLocalMetricsMXBeanImpl implements CacheMetricsMXBean {
    /** Cache. */
    private GridCacheAdapter<?, ?> cache;

    /**
     * Creates MBean;
     *
     * @param cache Cache.
     */
    CacheLocalMetricsMXBeanImpl(GridCacheAdapter<?, ?> cache) {
        assert cache != null;

        this.cache = cache;
    }

    /** {@inheritDoc} */
    @Override public String name() {
        return cache.metrics0().name();
    }

    /** {@inheritDoc} */
    @Override public long getOffHeapGets() {
        return cache.metrics0().getOffHeapGets();
    }

    /** {@inheritDoc} */
    @Override public long getOffHeapPuts() {
        return cache.metrics0().getOffHeapPuts();
    }

    /** {@inheritDoc} */
    @Override public long getOffHeapRemovals() {
        return cache.metrics0().getOffHeapRemovals();
    }

    /** {@inheritDoc} */
    @Override public long getOffHeapEvictions() {
        return cache.metrics0().getOffHeapEvictions();
    }

    /** {@inheritDoc} */
    @Override public long getOffHeapHits() {
        return cache.metrics0().getOffHeapHits();
    }

    /** {@inheritDoc} */
    @Override public float getOffHeapHitPercentage() {
        return cache.metrics0().getOffHeapHitPercentage();
    }

    /** {@inheritDoc} */
    @Override public long getOffHeapMisses() {
        return cache.metrics0().getOffHeapMisses();
    }

    /** {@inheritDoc} */
    @Override public float getOffHeapMissPercentage() {
        return cache.metrics0().getOffHeapMissPercentage();
    }

    /** {@inheritDoc} */
    @Override public long getOffHeapEntriesCount() {
        return cache.metrics0().getOffHeapEntriesCount();
    }

    /** {@inheritDoc} */
    @Override public long getHeapEntriesCount() {
        return cache.metrics0().getHeapEntriesCount();
    }

    /** {@inheritDoc} */
    @Override public long getOffHeapPrimaryEntriesCount() {
        return cache.metrics0().getOffHeapPrimaryEntriesCount();
    }

    /** {@inheritDoc} */
    @Override public long getOffHeapBackupEntriesCount() {
        return cache.metrics0().getOffHeapBackupEntriesCount();
    }

    /** {@inheritDoc} */
    @Override public long getOffHeapAllocatedSize() {
        return cache.metrics0().getOffHeapAllocatedSize();
    }

    /** {@inheritDoc} */
    @Override public int getSize() {
        return cache.metrics0().getSize();
    }

    /** {@inheritDoc} */
    @Override public long getCacheSize() {
        return cache.metrics0().getCacheSize();
    }

    /** {@inheritDoc} */
    @Override public int getKeySize() {
        return cache.metrics0().getKeySize();
    }

    /** {@inheritDoc} */
    @Override public boolean isEmpty() {
        return cache.metrics0().isEmpty();
    }

    /** {@inheritDoc} */
    @Override public int getDhtEvictQueueCurrentSize() {
        return cache.metrics0().getDhtEvictQueueCurrentSize();
    }

    /** {@inheritDoc} */
    @Override public int getTxCommitQueueSize() {
        return cache.metrics0().getTxCommitQueueSize();
    }

    /** {@inheritDoc} */
    @Override public int getTxThreadMapSize() {
        return cache.metrics0().getTxThreadMapSize();
    }

    /** {@inheritDoc} */
    @Override public int getTxXidMapSize() {
        return cache.metrics0().getTxXidMapSize();
    }

    /** {@inheritDoc} */
    @Override public int getTxPrepareQueueSize() {
        return cache.metrics0().getTxPrepareQueueSize();
    }

    /** {@inheritDoc} */
    @Override public int getTxStartVersionCountsSize() {
        return cache.metrics0().getTxStartVersionCountsSize();
    }

    /** {@inheritDoc} */
    @Override public int getTxCommittedVersionsSize() {
        return cache.metrics0().getTxCommittedVersionsSize();
    }

    /** {@inheritDoc} */
    @Override public int getTxRolledbackVersionsSize() {
        return cache.metrics0().getTxRolledbackVersionsSize();
    }

    /** {@inheritDoc} */
    @Override public int getTxDhtThreadMapSize() {
        return cache.metrics0().getTxDhtThreadMapSize();
    }

    /** {@inheritDoc} */
    @Override public int getTxDhtXidMapSize() {
        return cache.metrics0().getTxDhtXidMapSize();
    }

    /** {@inheritDoc} */
    @Override public int getTxDhtCommitQueueSize() {
        return cache.metrics0().getTxDhtCommitQueueSize();
    }

    /** {@inheritDoc} */
    @Override public int getTxDhtPrepareQueueSize() {
        return cache.metrics0().getTxDhtPrepareQueueSize();
    }

    /** {@inheritDoc} */
    @Override public int getTxDhtStartVersionCountsSize() {
        return cache.metrics0().getTxDhtStartVersionCountsSize();
    }

    /** {@inheritDoc} */
    @Override public int getTxDhtCommittedVersionsSize() {
        return cache.metrics0().getTxDhtCommittedVersionsSize();
    }

    /** {@inheritDoc} */
    @Override public int getTxDhtRolledbackVersionsSize() {
        return cache.metrics0().getTxDhtRolledbackVersionsSize();
    }

    /** {@inheritDoc} */
    @Override public boolean isWriteBehindEnabled() {
        return cache.metrics0().isWriteBehindEnabled();
    }

    /** {@inheritDoc} */
    @Override public int getWriteBehindFlushSize() {
        return cache.metrics0().getWriteBehindFlushSize();
    }

    /** {@inheritDoc} */
    @Override public int getWriteBehindFlushThreadCount() {
        return cache.metrics0().getWriteBehindFlushThreadCount();
    }

    /** {@inheritDoc} */
    @Override public long getWriteBehindFlushFrequency() {
        return cache.metrics0().getWriteBehindFlushFrequency();
    }

    /** {@inheritDoc} */
    @Override public int getWriteBehindStoreBatchSize() {
        return cache.metrics0().getWriteBehindStoreBatchSize();
    }

    /** {@inheritDoc} */
    @Override public int getWriteBehindTotalCriticalOverflowCount() {
        return cache.metrics0().getWriteBehindTotalCriticalOverflowCount();
    }

    /** {@inheritDoc} */
    @Override public int getWriteBehindCriticalOverflowCount() {
        return cache.metrics0().getWriteBehindCriticalOverflowCount();
    }

    /** {@inheritDoc} */
    @Override public int getWriteBehindErrorRetryCount() {
        return cache.metrics0().getWriteBehindErrorRetryCount();
    }

    /** {@inheritDoc} */
    @Override public int getWriteBehindBufferSize() {
        return cache.metrics0().getWriteBehindBufferSize();
    }

    /** {@inheritDoc} */
    @Override public void clear() {
        cache.metrics0().clear();
    }

    /** {@inheritDoc} */
    @Override public long getCacheHits() {
        return cache.metrics0().getCacheHits();
    }

    /** {@inheritDoc} */
    @Override public float getCacheHitPercentage() {
        return cache.metrics0().getCacheHitPercentage();
    }

    /** {@inheritDoc} */
    @Override public long getCacheMisses() {
        return cache.metrics0().getCacheMisses();
    }

    /** {@inheritDoc} */
    @Override public float getCacheMissPercentage() {
        return cache.metrics0().getCacheMissPercentage();
    }

    /** {@inheritDoc} */
    @Override public long getCacheGets() {
        return cache.metrics0().getCacheGets();
    }

    /** {@inheritDoc} */
    @Override public long getCachePuts() {
        return cache.metrics0().getCachePuts();
    }

    /** {@inheritDoc} */
    @Override public long getEntryProcessorPuts() {
        return cache.metrics0().getEntryProcessorPuts();
    }

    /** {@inheritDoc} */
    @Override public long getEntryProcessorRemovals() {
        return cache.metrics0().getEntryProcessorRemovals();
    }

    /** {@inheritDoc} */
    @Override public long getEntryProcessorReadOnlyInvocations() {
        return cache.metrics0().getEntryProcessorReadOnlyInvocations();
    }

    /** {@inheritDoc} */
    @Override public long getEntryProcessorInvocations() {
        return cache.metrics0().getEntryProcessorInvocations();
    }

    /** {@inheritDoc} */
    @Override public long getEntryProcessorHits() {
        return cache.metrics0().getEntryProcessorHits();
    }

    /** {@inheritDoc} */
    @Override public float getEntryProcessorHitPercentage() {
        return cache.metrics0().getEntryProcessorHitPercentage();
    }

    /** {@inheritDoc} */
    @Override public float getEntryProcessorMissPercentage() {
        return cache.metrics0().getEntryProcessorMissPercentage();
    }

    /** {@inheritDoc} */
    @Override public long getEntryProcessorMisses() {
        return cache.metrics0().getEntryProcessorMisses();
    }

    /** {@inheritDoc} */
    @Override public float getEntryProcessorAverageInvocationTime() {
        return cache.metrics0().getEntryProcessorAverageInvocationTime();
    }

    /** {@inheritDoc} */
    @Override public float getEntryProcessorMinInvocationTime() {
        return cache.metrics0().getEntryProcessorMinInvocationTime();
    }

    /** {@inheritDoc} */
    @Override public float getEntryProcessorMaxInvocationTime() {
        return cache.metrics0().getEntryProcessorMaxInvocationTime();
    }

    /** {@inheritDoc} */
    @Override public long getCacheRemovals() {
        return cache.metrics0().getCacheRemovals();
    }

    /** {@inheritDoc} */
    @Override public long getCacheEvictions() {
        return cache.metrics0().getCacheEvictions();
    }

    /** {@inheritDoc} */
    @Override public float getAverageGetTime() {
        return cache.metrics0().getAverageGetTime();
    }

    /** {@inheritDoc} */
    @Override public float getAveragePutTime() {
        return cache.metrics0().getAveragePutTime();
    }

    /** {@inheritDoc} */
    @Override public float getAverageRemoveTime() {
        return cache.metrics0().getAverageRemoveTime();
    }

    /** {@inheritDoc} */
    @Override public float getAverageTxCommitTime() {
        return cache.metrics0().getAverageTxCommitTime();
    }

    /** {@inheritDoc} */
    @Override public float getAverageTxRollbackTime() {
        return cache.metrics0().getAverageTxRollbackTime();
    }

    /** {@inheritDoc} */
    @Override public long getCacheTxCommits() {
        return cache.metrics0().getCacheTxCommits();
    }

    /** {@inheritDoc} */
    @Override public long getCacheTxRollbacks() {
        return cache.metrics0().getCacheTxRollbacks();
    }

    /** {@inheritDoc} */
    @Override public String getKeyType() {
        return cache.metrics0().getKeyType();
    }

    /** {@inheritDoc} */
    @Override public String getValueType() {
        return cache.metrics0().getValueType();
    }

    /** {@inheritDoc} */
    @Override public boolean isStoreByValue() {
        return cache.metrics0().isStoreByValue();
    }

    /** {@inheritDoc} */
    @Override public boolean isStatisticsEnabled() {
        return cache.metrics0().isStatisticsEnabled();
    }

    /** {@inheritDoc} */
    @Override public boolean isManagementEnabled() {
        return cache.metrics0().isManagementEnabled();
    }

    /** {@inheritDoc} */
    @Override public boolean isReadThrough() {
        return cache.metrics0().isReadThrough();
    }

    /** {@inheritDoc} */
    @Override public boolean isWriteThrough() {
        return cache.metrics0().isWriteThrough();
    }

    /** {@inheritDoc} */
    @Override public int getTotalPartitionsCount() {
        return cache.metrics0().getTotalPartitionsCount();
    }

    @Override public long getRebalancedKeys() {
        return cache.metrics0().getRebalancedKeys();
    }

    @Override public long getEstimatedRebalancingKeys() {
        return cache.metrics0().getEstimatedRebalancingKeys();
    }

    /** {@inheritDoc} */
    @Override public int getRebalancingPartitionsCount() {
        return cache.metrics0().getRebalancingPartitionsCount();
    }

    /** {@inheritDoc} */
    @Override public long getKeysToRebalanceLeft() {
        return cache.metrics0().getKeysToRebalanceLeft();
    }

    /** {@inheritDoc} */
    @Override public long getRebalancingKeysRate() {
        return cache.metrics0().getRebalancingKeysRate();
    }

    /** {@inheritDoc} */
    @Override public long getRebalancingBytesRate() {
        return cache.metrics0().getRebalancingBytesRate();
    }

    /** {@inheritDoc} */
    @Override public long estimateRebalancingFinishTime() {
        return cache.metrics0().estimateRebalancingFinishTime();
    }

    /** {@inheritDoc} */
    @Override public long rebalancingStartTime() {
        return cache.metrics0().rebalancingStartTime();
    }

    /** {@inheritDoc} */
    @Override public long getEstimatedRebalancingFinishTime() {
        return cache.metrics0().getEstimatedRebalancingFinishTime();
    }

    /** {@inheritDoc} */
    @Override public long getRebalancingStartTime() {
        return cache.metrics0().getRebalancingStartTime();
    }

    /** {@inheritDoc} */
    @Override public long getRebalanceClearingPartitionsLeft() {
        return cache.metrics0().getRebalanceClearingPartitionsLeft();
    }

    /** {@inheritDoc} */
    @Override public boolean isValidForReading() {
        return cache.metrics0().isValidForReading();
    }

    /** {@inheritDoc} */
    @Override public boolean isValidForWriting() {
        return cache.metrics0().isValidForWriting();
    }

    /** {@inheritDoc} */
    @Override public void enableStatistics() {
        try {
            cache.context().shared().cache().enableStatistics(Collections.singleton(cache.name()), true);
        }
        catch (IgniteCheckedException e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    /** {@inheritDoc} */
    @Override public void disableStatistics() {
        try {
            cache.context().shared().cache().enableStatistics(Collections.singleton(cache.name()), false);
        }
        catch (IgniteCheckedException e) {
            throw new RuntimeException(e.getMessage());
        }
    }
}
