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

package org.apache.ignite.ml.sql;

import org.apache.ignite.Ignite;
import org.apache.ignite.binary.BinaryObject;
import org.apache.ignite.lang.IgniteBiPredicate;
import org.apache.ignite.ml.dataset.UpstreamTransformerBuilder;
import org.apache.ignite.ml.dataset.impl.cache.CacheBasedDataset;
import org.apache.ignite.ml.dataset.impl.cache.CacheBasedDatasetBuilder;

/**
 * Subclass of {@link CacheBasedDatasetBuilder} that should be used to work with SQL tables.
 */
public class SqlDatasetBuilder extends CacheBasedDatasetBuilder<Object, BinaryObject> {
    /**
     * Constructs a new instance of cache based dataset builder that makes {@link CacheBasedDataset} with default
     * predicate that passes all upstream entries to dataset.
     *
     * @param ignite Ignite instance.
     * @param upstreamCache Name of Ignite Cache with {@code upstream} data.
     */
    public SqlDatasetBuilder(Ignite ignite, String upstreamCache) {
        this(ignite, upstreamCache, (a, b) -> true);
    }

    /**
     * Constructs a new instance of cache based dataset builder that makes {@link CacheBasedDataset}.
     *
     * @param ignite Ignite instance.
     * @param upstreamCache Name of Ignite Cache with {@code upstream} data.
     * @param filter Filter for {@code upstream} data.
     */
    public SqlDatasetBuilder(Ignite ignite, String upstreamCache, IgniteBiPredicate<Object, BinaryObject> filter) {
        this(ignite, upstreamCache, filter, UpstreamTransformerBuilder.identity());
    }

    /**
     * Constructs a new instance of cache based dataset builder that makes {@link CacheBasedDataset}.
     *
     * @param ignite Ignite instance.
     * @param upstreamCache Name of Ignite Cache with {@code upstream} data.
     * @param filter Filter for {@code upstream} data.
     */
    public SqlDatasetBuilder(Ignite ignite, String upstreamCache, IgniteBiPredicate<Object, BinaryObject> filter,
        UpstreamTransformerBuilder transformerBuilder) {
        super(ignite, ignite.cache(upstreamCache), filter, transformerBuilder, true);
    }
}
