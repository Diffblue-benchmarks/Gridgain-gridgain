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

import org.apache.ignite.internal.processors.cache.query.CacheQuery;

/**
 * Cache query metrics used to obtain statistics on query. Metrics for particular query
 * can be get via {@link CacheQuery#metrics()} method or aggregated metrics for all queries
 * via {@link CacheQuery#metrics()}.
 */
public interface QueryMetrics {
    /**
     * Gets minimum execution time of query.
     *
     * @return Minimum execution time of query.
     */
    public long minimumTime();

    /**
     * Gets maximum execution time of query.
     *
     * @return Maximum execution time of query.
     */
    public long maximumTime();

    /**
     * Gets average execution time of query.
     *
     * @return Average execution time of query.
     */
    public double averageTime();

    /**
     * Gets total number execution of query.
     *
     * @return Number of executions.
     */
    public int executions();

    /**
     * Gets total number of times a query execution failed.
     *
     * @return Total number of times a query execution failed.
     */
    public int fails();
}
