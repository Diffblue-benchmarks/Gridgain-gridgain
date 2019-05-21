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

package org.apache.ignite.internal.processors.query.h2;

import java.util.concurrent.atomic.AtomicLongFieldUpdater;
import org.apache.ignite.IgniteSystemProperties;
import org.apache.ignite.internal.mem.IgniteOutOfMemoryException;
import org.apache.ignite.internal.util.typedef.internal.S;

/**
 * Query memory tracker.
 *
 * Track query memory usage and throws an exception if query tries to allocate memory over limit.
 */
public class QueryMemoryTracker {
    //TODO: GG-18629: Move defaults to memory quotas configuration.
    /**
     * Default query memory limit.
     * Note: it is per query stage limit. Every Map\Reduce query will have it's own tracker.
     */
    public static final long DFLT_QRY_MEMORY_LIMIT = Long.getLong(IgniteSystemProperties.IGNITE_SQL_QUERY_MEMORY_LIMIT,
        Runtime.getRuntime().maxMemory() / Runtime.getRuntime().availableProcessors());

    /** Atomic field updater. */
    private static final AtomicLongFieldUpdater<QueryMemoryTracker> ALLOC_UPD = AtomicLongFieldUpdater.newUpdater(QueryMemoryTracker.class, "allocated");

    /** Memory limit. */
    private final long maxMem;

    /** Memory allocated. */
    private volatile long allocated;

    /**
     * Constructor.
     *
     * @param maxMem Query memory limit in bytes.
     * Note: If zero value, then {@link QueryMemoryTracker#DFLT_QRY_MEMORY_LIMIT} will be used.
     * Note: Long.MAX_VALUE is reserved for disable memory tracking.
     */
    public QueryMemoryTracker(long maxMem) {
        assert maxMem >= 0 && maxMem != Long.MAX_VALUE;

        this.maxMem = maxMem > 0 ? maxMem : DFLT_QRY_MEMORY_LIMIT;
    }

    /**
     * Check allocated size is less than query memory pool threshold.
     *
     * @param size Allocated size in bytes.
     * @throws IgniteOutOfMemoryException if memory limit has been exceeded.
     */
    public void allocate(long size) {
        assert size >= 0;

        if (ALLOC_UPD.addAndGet(this, size) >= maxMem)
            throw new IgniteOutOfMemoryException("SQL query out of memory");
    }

    /**
     * Free allocated memory.
     *
     * @param size Free size in bytes.
     */
    public void free(long size) {
        assert size >= 0;

        long allocated = ALLOC_UPD.addAndGet(this, -size);

        assert allocated >= 0 : "Invalid allocated memory size:" + allocated;
    }

    /**
     * @return Memory allocated by tracker.
     */
    public long getAllocated() {
        return allocated;
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(QueryMemoryTracker.class, this);
    }
}