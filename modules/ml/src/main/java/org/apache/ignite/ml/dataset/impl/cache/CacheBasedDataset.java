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

package org.apache.ignite.ml.dataset.impl.cache;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Collection;
import java.util.UUID;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.Ignition;
import org.apache.ignite.lang.IgniteBiPredicate;
import org.apache.ignite.ml.dataset.Dataset;
import org.apache.ignite.ml.dataset.DatasetBuilder;
import org.apache.ignite.ml.dataset.PartitionDataBuilder;
import org.apache.ignite.ml.dataset.UpstreamTransformerBuilder;
import org.apache.ignite.ml.dataset.impl.cache.util.ComputeUtils;
import org.apache.ignite.ml.environment.LearningEnvironment;
import org.apache.ignite.ml.environment.LearningEnvironmentBuilder;
import org.apache.ignite.ml.math.functions.IgniteBiFunction;
import org.apache.ignite.ml.math.functions.IgniteBinaryOperator;
import org.apache.ignite.ml.math.functions.IgniteFunction;
import org.apache.ignite.ml.math.functions.IgniteTriFunction;

/**
 * An implementation of dataset based on Ignite Cache, which is used as {@code upstream} and as reliable storage for
 * partition {@code context} as well.
 *
 * @param <K> Type of a key in {@code upstream} data.
 * @param <V> Type of a value in {@code upstream} data.
 * @param <C> Type of a partition {@code context}.
 * @param <D> Type of a partition {@code data}.
 */
public class CacheBasedDataset<K, V, C extends Serializable, D extends AutoCloseable>
    implements Dataset<C, D> {
    /** Number of retries for the case when one of partitions not found on the node where computation is performed. */
    private static final int RETRIES = 15 * 60;

    /** Retry interval (ms) for the case when one of partitions not found on the node where computation is performed. */
    private static final int RETRY_INTERVAL = 1000;

    /** Ignite instance. */
    private final Ignite ignite;

    /** Ignite Cache with {@code upstream} data. */
    private final IgniteCache<K, V> upstreamCache;

    /** Filter for {@code upstream} data. */
    private final IgniteBiPredicate<K, V> filter;

    /** Builder of transformation applied to upstream. */
    private final UpstreamTransformerBuilder upstreamTransformerBuilder;

    /** Ignite Cache with partition {@code context}. */
    private final IgniteCache<Integer, C> datasetCache;

    /** Partition {@code data} builder. */
    private final PartitionDataBuilder<K, V, C, D> partDataBuilder;

    /** Dataset ID that is used to identify dataset in local storage on the node where computation is performed. */
    private final UUID datasetId;

    /** Learning environment builder. */
    private final LearningEnvironmentBuilder envBuilder;

    /** Upstream keep binary. */
    private final boolean upstreamKeepBinary;

    /**
     * Constructs a new instance of dataset based on Ignite Cache, which is used as {@code upstream} and as reliable storage for
     * partition {@code context} as well.
     *
     * @param ignite Ignite instance.
     * @param upstreamCache Ignite Cache with {@code upstream} data.
     * @param filter Filter for {@code upstream} data.
     * @param upstreamTransformerBuilder Transformer of upstream data (see description in {@link DatasetBuilder}).
     * @param datasetCache Ignite Cache with partition {@code context}.
     * @param partDataBuilder Partition {@code data} builder.
     * @param datasetId Dataset ID.
     */
    public CacheBasedDataset(
        Ignite ignite,
        IgniteCache<K, V> upstreamCache,
        IgniteBiPredicate<K, V> filter,
        UpstreamTransformerBuilder upstreamTransformerBuilder,
        IgniteCache<Integer, C> datasetCache,
        LearningEnvironmentBuilder envBuilder,
        PartitionDataBuilder<K, V, C, D> partDataBuilder,
        UUID datasetId,
        boolean upstreamKeepBinary) {
        this.ignite = ignite;
        this.upstreamCache = upstreamCache;
        this.filter = filter;
        this.upstreamTransformerBuilder = upstreamTransformerBuilder;
        this.datasetCache = datasetCache;
        this.partDataBuilder = partDataBuilder;
        this.envBuilder = envBuilder;
        this.datasetId = datasetId;
        this.upstreamKeepBinary = upstreamKeepBinary;
    }

    /** {@inheritDoc} */
    @Override public <R> R computeWithCtx(IgniteTriFunction<C, D, LearningEnvironment, R> map, IgniteBinaryOperator<R> reduce, R identity) {
        String upstreamCacheName = upstreamCache.getName();
        String datasetCacheName = datasetCache.getName();

        return computeForAllPartitions(part -> {
            LearningEnvironment env = ComputeUtils.getLearningEnvironment(ignite, datasetId, part, envBuilder);

            C ctx = ComputeUtils.getContext(Ignition.localIgnite(), datasetCacheName, part);

            D data = ComputeUtils.getData(
                Ignition.localIgnite(),
                upstreamCacheName,
                filter,
                upstreamTransformerBuilder,
                datasetCacheName,
                datasetId,
                partDataBuilder,
                env,
                upstreamKeepBinary
            );


            if (data != null) {
                R res = map.apply(ctx, data, env);

                // Saves partition context after update.
                ComputeUtils.saveContext(Ignition.localIgnite(), datasetCacheName, part, ctx);

                return res;
            }

            return null;
        }, reduce, identity);
    }

    /** {@inheritDoc} */
    @Override public <R> R compute(IgniteBiFunction<D, LearningEnvironment, R> map, IgniteBinaryOperator<R> reduce, R identity) {
        String upstreamCacheName = upstreamCache.getName();
        String datasetCacheName = datasetCache.getName();

        return computeForAllPartitions(part -> {
            LearningEnvironment env = ComputeUtils.getLearningEnvironment(Ignition.localIgnite(), datasetId, part, envBuilder);

            D data = ComputeUtils.getData(
                Ignition.localIgnite(),
                upstreamCacheName,
                filter,
                upstreamTransformerBuilder,
                datasetCacheName,
                datasetId,
                partDataBuilder,
                env,
                upstreamKeepBinary
            );
            return data != null ? map.apply(data, env) : null;
        }, reduce, identity);
    }

    /** {@inheritDoc} */
    @Override public void close() {
        datasetCache.destroy();
        ComputeUtils.removeData(ignite, datasetId);
        ComputeUtils.removeLearningEnv(ignite, datasetId);
    }

    /**
     * Calls the {@code MapReduce} job specified as the {@code fun} function and the {@code reduce} reducer on all
     * partitions with guarantee that partitions with the same index of upstream and partition {@code context} caches
     * will be on the same node during the computation and will not be moved before computation is finished.
     *
     * @param fun Function that applies to all partitions.
     * @param reduce Function that reduces results of {@code fun}.
     * @param identity Identity.
     * @param <R> Type of a result.
     * @return Final result.
     */
    private <R> R computeForAllPartitions(IgniteFunction<Integer, R> fun, IgniteBinaryOperator<R> reduce, R identity) {
        Collection<String> cacheNames = Arrays.asList(datasetCache.getName(), upstreamCache.getName());
        Collection<R> results = ComputeUtils.affinityCallWithRetries(ignite, cacheNames, fun, RETRIES, RETRY_INTERVAL);

        R res = identity;
        for (R partRes : results)
            if (partRes != null)
                res = reduce.apply(res, partRes);

        return res;
    }

    /** */
    public IgniteCache<K, V> getUpstreamCache() {
        return upstreamCache;
    }

    /** */
    public IgniteCache<Integer, C> getDatasetCache() {
        return datasetCache;
    }
}
