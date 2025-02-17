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

package org.apache.ignite.cache.query;

import javax.cache.Cache;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.cache.CacheAtomicityMode;
import org.apache.ignite.configuration.DataRegionConfiguration;
import org.apache.ignite.internal.util.typedef.internal.S;
import org.apache.ignite.lang.IgniteBiPredicate;
import org.jetbrains.annotations.Nullable;

/**
 * Scan query over cache entries. Will accept all the entries if no predicate was set.
 *
 * @see IgniteCache#query(Query)
 */
public final class ScanQuery<K, V> extends Query<Cache.Entry<K, V>> {
    /** */
    private static final long serialVersionUID = 0L;

    /** */
    private IgniteBiPredicate<K, V> filter;

    /** */
    private Integer part;

    /** */
    private Boolean dataPageScanEnabled;

    /**
     * Create scan query returning all entries.
     */
    public ScanQuery() {
        this(null, null);
    }

    /**
     * Creates partition scan query returning all entries for given partition.
     *
     * @param part Partition.
     */
    public ScanQuery(int part) {
        this(part, null);
    }

    /**
     * Create scan query with filter.
     *
     * @param filter Filter. If {@code null} then all entries will be returned.
     */
    public ScanQuery(@Nullable IgniteBiPredicate<K, V> filter) {
        this(null, filter);
    }

    /**
     * Create scan query with filter.
     *
     * @param part Partition.
     * @param filter Filter. If {@code null} then all entries will be returned.
     */
    public ScanQuery(@Nullable Integer part, @Nullable IgniteBiPredicate<K, V> filter) {
        setPartition(part);
        setFilter(filter);
    }

    /**
     * Gets filter.
     *
     * @return Filter.
     */
    public IgniteBiPredicate<K, V> getFilter() {
        return filter;
    }

    /**
     * Sets filter.
     *
     * @param filter Filter. If {@code null} then all entries will be returned.
     * @return {@code this} for chaining.
     */
    public ScanQuery<K, V> setFilter(@Nullable IgniteBiPredicate<K, V> filter) {
        this.filter = filter;

        return this;
    }

    /**
     * Sets partition number over which this query should iterate. If {@code null}, query will iterate over
     * all partitions in the cache. Must be in the range [0, N) where N is partition number in the cache.
     *
     * @param part Partition number over which this query should iterate.
     * @return {@code this} for chaining.
     */
    public ScanQuery<K, V> setPartition(@Nullable Integer part) {
        this.part = part;

        return this;
    }

    /**
     * Gets partition number over which this query should iterate. Will return {@code null} if partition was not
     * set. In this case query will iterate over all partitions in the cache.
     *
     * @return Partition number or {@code null}.
     */
    @Nullable public Integer getPartition() {
        return part;
    }

    /**
     * Sets data page scan enabled or disabled.
     *
     * Makes sense only with enabled {@link DataRegionConfiguration#setPersistenceEnabled persistence}
     * and generally improves performance.
     * When enabled, result may miss some concurrent updates or produce duplicates for the same key.
     * To avoid these issues use with {@link CacheAtomicityMode#TRANSACTIONAL_SNAPSHOT}.
     *
     * @param dataPageScanEnabled {@code true} If data page scan enabled, {@code false} if not, and {@code null} if not set.
     * @return {@code this} for chaining.
     */
    public ScanQuery<K, V> setDataPageScanEnabled(Boolean dataPageScanEnabled) {
        this.dataPageScanEnabled = dataPageScanEnabled;

        return this;
    }

    /**
     * Checks if data page scan enabled.
     *
     * @return {@code true} If data page scan enabled, {@code false} if not, and {@code null} if not set.
     */
    public Boolean isDataPageScanEnabled() {
        return dataPageScanEnabled;
    }

    /** {@inheritDoc} */
    @Override public ScanQuery<K, V> setPageSize(int pageSize) {
        return (ScanQuery<K, V>)super.setPageSize(pageSize);
    }

    /** {@inheritDoc} */
    @Override public ScanQuery<K, V> setLocal(boolean loc) {
        return (ScanQuery<K, V>)super.setLocal(loc);
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(ScanQuery.class, this);
    }
}