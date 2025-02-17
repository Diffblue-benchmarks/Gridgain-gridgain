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

package org.apache.ignite.ml.dataset;

import java.io.Serializable;
import java.util.Map;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.ml.dataset.impl.cache.CacheBasedDatasetBuilder;
import org.apache.ignite.ml.dataset.impl.local.LocalDatasetBuilder;
import org.apache.ignite.ml.dataset.primitive.SimpleDataset;
import org.apache.ignite.ml.dataset.primitive.SimpleLabeledDataset;
import org.apache.ignite.ml.dataset.primitive.builder.context.EmptyContextBuilder;
import org.apache.ignite.ml.dataset.primitive.builder.data.SimpleDatasetDataBuilder;
import org.apache.ignite.ml.dataset.primitive.builder.data.SimpleLabeledDatasetDataBuilder;
import org.apache.ignite.ml.dataset.primitive.context.EmptyContext;
import org.apache.ignite.ml.dataset.primitive.data.SimpleDatasetData;
import org.apache.ignite.ml.dataset.primitive.data.SimpleLabeledDatasetData;
import org.apache.ignite.ml.environment.LearningEnvironmentBuilder;
import org.apache.ignite.ml.preprocessing.Preprocessor;

/**
 * Factory providing a client facing API that allows to construct basic and the most frequently used types of dataset.
 *
 *
 * <p>Dataset construction is based on three major concepts: a partition {@code upstream}, {@code context} and
 * {@code data}. A partition {@code upstream} is a data source, which assumed to be available all the time regardless
 * node failures and rebalancing events. A partition {@code context} is a part of a partition maintained during the
 * whole computation process and stored in a reliable storage so that a {@code context} is staying available and
 * consistent regardless node failures and rebalancing events as well as an {@code upstream}. A partition {@code data}
 * is a part of partition maintained during a computation process in unreliable local storage such as heap, off-heap or
 * GPU memory on the node where current computation is performed, so that partition {@code data} can be lost as result
 * of node failure or rebalancing, but it can be restored from an {@code upstream} and a partition {@code context}.
 *
 * <p>A partition {@code context} and {@code data} are built on top of an {@code upstream} by using specified
 * builders: {@link PartitionContextBuilder} and {@link PartitionDataBuilder} correspondingly. To build a generic
 * dataset the following approach is used:
 *
 * <code>
 * {@code Dataset<C, D> dataset = DatasetFactory.create( ignite, cache, partitionContextBuilder, partitionDataBuilder );
 * }
 * </code>
 *
 * <p>As well as the generic building method {@code create} this factory provides methods that allow to create a
 * specific dataset types such as method {@code createSimpleDataset} to create {@link SimpleDataset} and method {@code
 * createSimpleLabeledDataset} to create {@link SimpleLabeledDataset}.
 *
 * @see Dataset
 * @see PartitionContextBuilder
 * @see PartitionDataBuilder
 */
public class DatasetFactory {
    /**
     * Creates a new instance of distributed dataset using the specified {@code partCtxBuilder} and {@code
     * partDataBuilder}. This is the generic methods that allows to create any Ignite Cache based datasets with any
     * desired partition {@code context} and {@code data}.
     *
     * @param envBuilder Learning environment builder.
     * @param datasetBuilder Dataset builder.
     * @param partCtxBuilder Partition {@code context} builder.
     * @param partDataBuilder Partition {@code data} builder.
     * @param <K> Type of a key in {@code upstream} data.
     * @param <V> ype of a value in {@code upstream} data.
     * @param <C> Type of a partition {@code context}.
     * @param <D> Type of a partition {@code data}.
     * @return Dataset.
     */
    public static <K, V, C extends Serializable, D extends AutoCloseable> Dataset<C, D> create(
        DatasetBuilder<K, V> datasetBuilder,
        LearningEnvironmentBuilder envBuilder,
        PartitionContextBuilder<K, V, C> partCtxBuilder,
        PartitionDataBuilder<K, V, C, D> partDataBuilder) {
        return datasetBuilder.build(
            envBuilder,
            partCtxBuilder,
            partDataBuilder
        );
    }

    /**
     * Creates a new instance of distributed dataset using the specified {@code partCtxBuilder} and {@code
     * partDataBuilder}. This is the generic methods that allows to create any Ignite Cache based datasets with any
     * desired partition {@code context} and {@code data}.
     *
     * @param datasetBuilder Dataset builder.
     * @param partCtxBuilder Partition {@code context} builder.
     * @param partDataBuilder Partition {@code data} builder.
     * @param <K> Type of a key in {@code upstream} data.
     * @param <V> ype of a value in {@code upstream} data.
     * @param <C> Type of a partition {@code context}.
     * @param <D> Type of a partition {@code data}.
     * @return Dataset.
     */
    public static <K, V, C extends Serializable, D extends AutoCloseable> Dataset<C, D> create(
        DatasetBuilder<K, V> datasetBuilder,
        PartitionContextBuilder<K, V, C> partCtxBuilder,
        PartitionDataBuilder<K, V, C, D> partDataBuilder) {
        return datasetBuilder.build(
            LearningEnvironmentBuilder.defaultBuilder(),
            partCtxBuilder,
            partDataBuilder
        );
    }

    /**
     * Creates a new instance of distributed dataset using the specified {@code partCtxBuilder} and {@code
     * partDataBuilder}. This is the generic methods that allows to create any Ignite Cache based datasets with any
     * desired partition {@code context} and {@code data}.
     *
     * @param ignite Ignite instance.
     * @param upstreamCache Ignite Cache with {@code upstream} data.
     * @param envBuilder Learning environment builder.
     * @param partCtxBuilder Partition {@code context} builder.
     * @param partDataBuilder Partition {@code data} builder.
     * @param <K> Type of a key in {@code upstream} data.
     * @param <V> Type of a value in {@code upstream} data.
     * @param <C> Type of a partition {@code context}.
     * @param <D> Type of a partition {@code data}.
     * @return Dataset.
     */
    public static <K, V, C extends Serializable, D extends AutoCloseable> Dataset<C, D> create(
        Ignite ignite, IgniteCache<K, V> upstreamCache,
        LearningEnvironmentBuilder envBuilder,
        PartitionContextBuilder<K, V, C> partCtxBuilder,
        PartitionDataBuilder<K, V, C, D> partDataBuilder) {
        return create(
            new CacheBasedDatasetBuilder<>(ignite, upstreamCache),
            envBuilder,
            partCtxBuilder,
            partDataBuilder
        );
    }

    /**
     * Creates a new instance of distributed dataset using the specified {@code partCtxBuilder} and {@code
     * partDataBuilder}. This is the generic methods that allows to create any Ignite Cache based datasets with any
     * desired partition {@code context} and {@code data}.
     *
     * @param ignite Ignite instance.
     * @param upstreamCache Ignite Cache with {@code upstream} data.
     * @param partCtxBuilder Partition {@code context} builder.
     * @param partDataBuilder Partition {@code data} builder.
     * @param <K> Type of a key in {@code upstream} data.
     * @param <V> Type of a value in {@code upstream} data.
     * @param <C> Type of a partition {@code context}.
     * @param <D> Type of a partition {@code data}.
     * @return Dataset.
     */
    public static <K, V, C extends Serializable, D extends AutoCloseable> Dataset<C, D> create(
        Ignite ignite, IgniteCache<K, V> upstreamCache,
        PartitionContextBuilder<K, V, C> partCtxBuilder,
        PartitionDataBuilder<K, V, C, D> partDataBuilder) {
        return create(
            new CacheBasedDatasetBuilder<>(ignite, upstreamCache),
            partCtxBuilder,
            partDataBuilder
        );
    }

    /**
     * Creates a new instance of distributed {@link SimpleDataset} using the specified {@code partCtxBuilder} and {@code
     * featureExtractor}. This methods determines partition {@code data} to be {@link SimpleDatasetData}, but allows to
     * use any desired type of partition {@code context}.
     *
     * @param datasetBuilder Dataset builder.
     * @param envBuilder Learning environment builder.
     * @param partCtxBuilder Partition {@code context} builder.
     * @param featureExtractor Feature extractor used to extract features and build {@link SimpleDatasetData}.
     * @param <K> Type of a key in {@code upstream} data.
     * @param <V> Type of a value in {@code upstream} data.
     * @param <C> Type of a partition {@code context}.
     * @return Dataset.
     */
    public static <K, V, C extends Serializable, CO extends Serializable> SimpleDataset<C> createSimpleDataset(
        DatasetBuilder<K, V> datasetBuilder,
        LearningEnvironmentBuilder envBuilder,
        PartitionContextBuilder<K, V, C> partCtxBuilder,
        Preprocessor<K, V> featureExtractor) {
        return create(
            datasetBuilder,
            envBuilder,
            partCtxBuilder,
            new SimpleDatasetDataBuilder<>(featureExtractor)
        ).wrap(SimpleDataset::new);
    }

    /**
     * Creates a new instance of distributed {@link SimpleDataset} using the specified {@code partCtxBuilder} and {@code
     * featureExtractor}. This methods determines partition {@code data} to be {@link SimpleDatasetData}, but allows to
     * use any desired type of partition {@code context}.
     *
     * @param ignite Ignite instance.
     * @param upstreamCache Ignite Cache with {@code upstream} data.
     * @param envBuilder Learning environment builder.
     * @param partCtxBuilder Partition {@code context} builder.
     * @param featureExtractor Feature extractor used to extract features and build {@link SimpleDatasetData}.
     * @param <K> Type of a key in {@code upstream} data.
     * @param <V> Type of a value in {@code upstream} data.
     * @param <C> Type of a partition {@code context}.
     * @return Dataset.
     */
    public static <K, V, C extends Serializable, CO extends Serializable> SimpleDataset<C> createSimpleDataset(
        Ignite ignite,
        IgniteCache<K, V> upstreamCache,
        LearningEnvironmentBuilder envBuilder,
        PartitionContextBuilder<K, V, C> partCtxBuilder,
        Preprocessor<K, V> featureExtractor) {
        return createSimpleDataset(
            new CacheBasedDatasetBuilder<>(ignite, upstreamCache),
            envBuilder,
            partCtxBuilder,
            featureExtractor
        );
    }

    /**
     * Creates a new instance of distributed {@link SimpleLabeledDataset} using the specified {@code partCtxBuilder},
     * {@code featureExtractor} and {@code lbExtractor}. This method determines partition {@code data} to be {@link
     * SimpleLabeledDatasetData}, but allows to use any desired type of partition {@code context}.
     *
     * @param datasetBuilder Dataset builder.
     * @param envBuilder Learning environment builder.
     * @param partCtxBuilder Partition {@code context} builder.
     * @param vectorizer Upstream vectorizer used to extract features and labels and build {@link
     * SimpleLabeledDatasetData}.
     * @param <K> Type of a key in {@code upstream} data.
     * @param <V> Type of a value in {@code upstream} data.
     * @param <C> Type of a partition {@code context}.
     * @return Dataset.
     */
    public static <K, V, C extends Serializable, CO extends Serializable> SimpleLabeledDataset<C> createSimpleLabeledDataset(
        DatasetBuilder<K, V> datasetBuilder,
        LearningEnvironmentBuilder envBuilder,
        PartitionContextBuilder<K, V, C> partCtxBuilder,
        Preprocessor<K, V> vectorizer) {
        return create(
            datasetBuilder,
            envBuilder,
            partCtxBuilder,
            new SimpleLabeledDatasetDataBuilder<>(vectorizer)
        ).wrap(SimpleLabeledDataset::new);
    }

    /**
     * Creates a new instance of distributed {@link SimpleLabeledDataset} using the specified {@code partCtxBuilder},
     * {@code featureExtractor} and {@code lbExtractor}. This method determines partition {@code data} to be {@link
     * SimpleLabeledDatasetData}, but allows to use any desired type of partition {@code context}.
     *
     * @param ignite Ignite instance.
     * @param upstreamCache Ignite Cache with {@code upstream} data.
     * @param envBuilder Learning environment builder.
     * @param partCtxBuilder Partition {@code context} builder.
     * @param vectorizer Upstream vectorizer used to extract features and labels and build {@link
     * SimpleLabeledDatasetData}.
     * @param <K> Type of a key in {@code upstream} data.
     * @param <V> Type of a value in {@code upstream} data.
     * @param <C> Type of a partition {@code context}.
     * @return Dataset.
     */
    public static <K, V, C extends Serializable, CO extends Serializable> SimpleLabeledDataset<C> createSimpleLabeledDataset(
        Ignite ignite,
        IgniteCache<K, V> upstreamCache,
        LearningEnvironmentBuilder envBuilder,
        PartitionContextBuilder<K, V, C> partCtxBuilder,
        Preprocessor<K, V> vectorizer) {
        return createSimpleLabeledDataset(
            new CacheBasedDatasetBuilder<>(ignite, upstreamCache),
            envBuilder,
            partCtxBuilder,
            vectorizer
        );
    }

    /**
     * Creates a new instance of distributed {@link SimpleDataset} using the specified {@code featureExtractor}. This
     * methods determines partition {@code context} to be {@link EmptyContext} and partition {@code data} to be {@link
     * SimpleDatasetData}.
     *
     * @param datasetBuilder Dataset builder.
     * @param envBuilder Learning environment builder.
     * @param featureExtractor Feature extractor used to extract features and build {@link SimpleDatasetData}.
     * @param <K> Type of a key in {@code upstream} data.
     * @param <V> Type of a value in {@code upstream} data.
     * @return Dataset.
     */
    public static <K, V, CO extends Serializable> SimpleDataset<EmptyContext> createSimpleDataset(
        DatasetBuilder<K, V> datasetBuilder,
        LearningEnvironmentBuilder envBuilder,
        Preprocessor<K, V> featureExtractor) {
        return createSimpleDataset(
            datasetBuilder,
            envBuilder,
            new EmptyContextBuilder<>(),
            featureExtractor
        );
    }

    /**
     * Creates a new instance of distributed {@link SimpleDataset} using the specified {@code featureExtractor}. This
     * methods determines partition {@code context} to be {@link EmptyContext} and partition {@code data} to be {@link
     * SimpleDatasetData}.
     *
     * @param ignite Ignite instance.
     * @param upstreamCache Ignite Cache with {@code upstream} data.
     * @param envBuilder Learning environment builder.
     * @param featureExtractor Feature extractor used to extract features and build {@link SimpleDatasetData}.
     * @param <K> Type of a key in {@code upstream} data.
     * @param <V> Type of a value in {@code upstream} data.
     * @return Dataset.
     */
    public static <K, V, CO extends Serializable> SimpleDataset<EmptyContext> createSimpleDataset(
        Ignite ignite,
        IgniteCache<K, V> upstreamCache,
        LearningEnvironmentBuilder envBuilder,
        Preprocessor<K, V> featureExtractor) {
        return createSimpleDataset(
            new CacheBasedDatasetBuilder<>(ignite, upstreamCache),
            envBuilder,
            featureExtractor
        );
    }

    /**
     * Creates a new instance of distributed {@link SimpleDataset} using the specified {@code featureExtractor}. This
     * methods determines partition {@code context} to be {@link EmptyContext} and partition {@code data} to be {@link
     * SimpleDatasetData}.
     *
     * @param ignite Ignite instance.
     * @param upstreamCache Ignite Cache with {@code upstream} data.
     * @param featureExtractor Feature extractor used to extract features and build {@link SimpleDatasetData}.
     * @param <K> Type of a key in {@code upstream} data.
     * @param <V> Type of a value in {@code upstream} data.
     * @return Dataset.
     */
    public static <K, V, CO extends Serializable> SimpleDataset<EmptyContext> createSimpleDataset(
        Ignite ignite,
        IgniteCache<K, V> upstreamCache,
        Preprocessor<K, V> featureExtractor) {
        return createSimpleDataset(
            new CacheBasedDatasetBuilder<>(ignite, upstreamCache),
            LearningEnvironmentBuilder.defaultBuilder(),
            featureExtractor
        );
    }

    /**
     * Creates a new instance of distributed {@link SimpleLabeledDataset} using the specified {@code featureExtractor}
     * and {@code lbExtractor}. This methods determines partition {@code context} to be {@link EmptyContext} and
     * partition {@code data} to be {@link SimpleLabeledDatasetData}.
     *
     * @param datasetBuilder Dataset builder.
     * @param envBuilder Learning environment builder.
     * @param vectorizer Upstream vectorizer used to extract features and labels and build {@link
     * SimpleLabeledDatasetData}.
     * @param <K> Type of a key in {@code upstream} data.
     * @param <V> Type of a value in {@code upstream} data.
     * @return Dataset.
     */
    public static <K, V, CO extends Serializable> SimpleLabeledDataset<EmptyContext> createSimpleLabeledDataset(
        DatasetBuilder<K, V> datasetBuilder,
        LearningEnvironmentBuilder envBuilder,
        Preprocessor<K, V> vectorizer) {
        return createSimpleLabeledDataset(
            datasetBuilder,
            envBuilder,
            new EmptyContextBuilder<>(),
            vectorizer
        );
    }

    /**
     * Creates a new instance of distributed {@link SimpleLabeledDataset} using the specified {@code featureExtractor}
     * and {@code lbExtractor}. This methods determines partition {@code context} to be {@link EmptyContext} and
     * partition {@code data} to be {@link SimpleLabeledDatasetData}.
     *
     * @param ignite Ignite instance.
     * @param upstreamCache Ignite Cache with {@code upstream} data.
     * @param envBuilder Learning environment builder.
     * @param vectorizer Upstream vectorizer used to extract features and labels and build {@link
     * SimpleLabeledDatasetData}.
     * @param <K> Type of a key in {@code upstream} data.
     * @param <V> Type of a value in {@code upstream} data.
     * @return Dataset.
     */
    public static <K, V, CO extends Serializable> SimpleLabeledDataset<EmptyContext> createSimpleLabeledDataset(
        Ignite ignite,
        LearningEnvironmentBuilder envBuilder,
        IgniteCache<K, V> upstreamCache,
        Preprocessor<K, V> vectorizer) {
        return createSimpleLabeledDataset(
            new CacheBasedDatasetBuilder<>(ignite, upstreamCache),
            envBuilder,
            vectorizer
        );
    }

    /**
     * Creates a new instance of local dataset using the specified {@code partCtxBuilder} and {@code partDataBuilder}.
     * This is the generic methods that allows to create any Ignite Cache based datasets with any desired partition
     * {@code context} and {@code data}.
     *
     * @param upstreamMap {@code Map} with {@code upstream} data.
     * @param partitions Number of partitions {@code upstream} {@code Map} will be divided on.
     * @param partCtxBuilder Partition {@code context} builder.
     * @param envBuilder Learning environment builder.
     * @param partDataBuilder Partition {@code data} builder.
     * @param <K> Type of a key in {@code upstream} data.
     * @param <V> Type of a value in {@code upstream} data.
     * @param <C> Type of a partition {@code context}.
     * @param <D> Type of a partition {@code data}.
     * @return Dataset.
     */
    public static <K, V, C extends Serializable, D extends AutoCloseable> Dataset<C, D> create(
        Map<K, V> upstreamMap,
        LearningEnvironmentBuilder envBuilder,
        int partitions, PartitionContextBuilder<K, V, C> partCtxBuilder,
        PartitionDataBuilder<K, V, C, D> partDataBuilder) {
        return create(
            new LocalDatasetBuilder<>(upstreamMap, partitions),
            envBuilder,
            partCtxBuilder,
            partDataBuilder
        );
    }

    /**
     * Creates a new instance of local {@link SimpleDataset} using the specified {@code partCtxBuilder} and {@code
     * featureExtractor}. This methods determines partition {@code data} to be {@link SimpleDatasetData}, but allows to
     * use any desired type of partition {@code context}.
     *
     * @param upstreamMap {@code Map} with {@code upstream} data.
     * @param partitions Number of partitions {@code upstream} {@code Map} will be divided on.
     * @param envBuilder Learning environment builder.
     * @param partCtxBuilder Partition {@code context} builder.
     * @param featureExtractor Feature extractor used to extract features and build {@link SimpleDatasetData}.
     * @param <K> Type of a key in {@code upstream} data.
     * @param <V> Type of a value in {@code upstream} data.
     * @param <C> Type of a partition {@code context}.
     * @return Dataset.
     */
    public static <K, V, C extends Serializable, CO extends Serializable> SimpleDataset<C> createSimpleDataset(
        Map<K, V> upstreamMap,
        int partitions,
        LearningEnvironmentBuilder envBuilder,
        PartitionContextBuilder<K, V, C> partCtxBuilder,
        Preprocessor<K, V> featureExtractor) {
        return createSimpleDataset(
            new LocalDatasetBuilder<>(upstreamMap, partitions),
            envBuilder,
            partCtxBuilder,
            featureExtractor
        );
    }

    /**
     * Creates a new instance of local {@link SimpleLabeledDataset} using the specified {@code partCtxBuilder}, {@code
     * featureExtractor} and {@code lbExtractor}. This method determines partition {@code data} to be {@link
     * SimpleLabeledDatasetData}, but allows to use any desired type of partition {@code context}.
     *
     * @param upstreamMap {@code Map} with {@code upstream} data.
     * @param partitions Number of partitions {@code upstream} {@code Map} will be divided on.
     * @param envBuilder Learning environment builder.
     * @param partCtxBuilder Partition {@code context} builder.
     * @param vectorizer Upstream vectorizer used to extract features and labels and build {@link
     * SimpleLabeledDatasetData}.
     * @param <K> Type of a key in {@code upstream} data.
     * @param <V> Type of a value in {@code upstream} data.
     * @param <C> Type of a partition {@code context}.
     * @return Dataset.
     */
    public static <K, V, C extends Serializable, CO extends Serializable> SimpleLabeledDataset<C> createSimpleLabeledDataset(
        Map<K, V> upstreamMap,
        int partitions,
        LearningEnvironmentBuilder envBuilder,
        PartitionContextBuilder<K, V, C> partCtxBuilder,
        Preprocessor<K, V> vectorizer) {
        return createSimpleLabeledDataset(
            new LocalDatasetBuilder<>(upstreamMap, partitions),
            envBuilder,
            partCtxBuilder,
            vectorizer
        );
    }

    /**
     * Creates a new instance of local {@link SimpleDataset} using the specified {@code featureExtractor}. This methods
     * determines partition {@code context} to be {@link EmptyContext} and partition {@code data} to be {@link
     * SimpleDatasetData}.
     *
     * @param upstreamMap {@code Map} with {@code upstream} data.
     * @param partitions Number of partitions {@code upstream} {@code Map} will be divided on.
     * @param envBuilder Learning environment builder.
     * @param featureExtractor Feature extractor used to extract features and build {@link SimpleDatasetData}.
     * @param <K> Type of a key in {@code upstream} data.
     * @param <V> Type of a value in {@code upstream} data.
     * @return Dataset.
     */
    public static <K, V, CO extends Serializable> SimpleDataset<EmptyContext> createSimpleDataset(Map<K, V> upstreamMap,
                                                                                                  int partitions,
                                                                                                  LearningEnvironmentBuilder envBuilder,
                                                                                                  Preprocessor<K, V> featureExtractor) {
        return createSimpleDataset(
            new LocalDatasetBuilder<>(upstreamMap, partitions),
            envBuilder,
            featureExtractor
        );
    }

    /**
     * Creates a new instance of local {@link SimpleLabeledDataset} using the specified {@code featureExtractor} and
     * {@code lbExtractor}. This methods determines partition {@code context} to be {@link EmptyContext} and partition
     * {@code data} to be {@link SimpleLabeledDatasetData}.
     *
     * @param upstreamMap {@code Map} with {@code upstream} data.
     * @param partitions Number of partitions {@code upstream} {@code Map} will be divided on.
     * @param envBuilder Learning environment builder.
     * @param vectorizer Upstream vectorizer used to extract features and labels and build {@link
     * SimpleLabeledDatasetData}.
     * @param <K> Type of a key in {@code upstream} data.
     * @param <V> Type of a value in {@code upstream} data.
     * @return Dataset.
     */
    public static <K, V, CO extends Serializable> SimpleLabeledDataset<EmptyContext> createSimpleLabeledDataset(
        Map<K, V> upstreamMap,
        LearningEnvironmentBuilder envBuilder,
        int partitions, Preprocessor<K, V> vectorizer) {
        return createSimpleLabeledDataset(
            new LocalDatasetBuilder<>(upstreamMap, partitions),
            envBuilder,
            vectorizer
        );
    }
}
