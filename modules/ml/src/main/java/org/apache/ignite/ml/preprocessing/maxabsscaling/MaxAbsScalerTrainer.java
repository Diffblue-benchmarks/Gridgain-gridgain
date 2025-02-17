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

package org.apache.ignite.ml.preprocessing.maxabsscaling;

import org.apache.ignite.ml.dataset.Dataset;
import org.apache.ignite.ml.dataset.DatasetBuilder;
import org.apache.ignite.ml.dataset.UpstreamEntry;
import org.apache.ignite.ml.dataset.primitive.context.EmptyContext;
import org.apache.ignite.ml.environment.LearningEnvironmentBuilder;
import org.apache.ignite.ml.preprocessing.PreprocessingTrainer;
import org.apache.ignite.ml.preprocessing.Preprocessor;
import org.apache.ignite.ml.structures.LabeledVector;

/**
 * Trainer of the maxabsscaling preprocessor.
 *
 * @param <K> Type of a key in {@code upstream} data.
 * @param <V> Type of a value in {@code upstream} data.
 */
public class MaxAbsScalerTrainer<K, V> implements PreprocessingTrainer<K, V> {
    /** {@inheritDoc} */
    @Override public MaxAbsScalerPreprocessor<K, V> fit(
        LearningEnvironmentBuilder envBuilder,
        DatasetBuilder<K, V> datasetBuilder,
        Preprocessor<K, V> basePreprocessor) {
        try (Dataset<EmptyContext, MaxAbsScalerPartitionData> dataset = datasetBuilder.build(
            envBuilder,
            (env, upstream, upstreamSize) -> new EmptyContext(),
            (env, upstream, upstreamSize, ctx) -> {
                double[] maxAbs = null;

                while (upstream.hasNext()) {
                    UpstreamEntry<K, V> entity = upstream.next();
                    LabeledVector row = basePreprocessor.apply(entity.getKey(), entity.getValue());

                    if (maxAbs == null) {
                        maxAbs = new double[row.size()];
                        for (int i = 0; i < maxAbs.length; i++)
                            maxAbs[i] = .0;
                    }
                    else
                        assert maxAbs.length == row.size() : "Base preprocessor must return exactly " + maxAbs.length
                            + " features";

                    for (int i = 0; i < row.size(); i++) {
                        if (Math.abs(row.get(i)) > Math.abs(maxAbs[i]))
                            maxAbs[i] = Math.abs(row.get(i));
                    }
                }
                return new MaxAbsScalerPartitionData(maxAbs);
            }
        )) {
            double[] maxAbs = dataset.compute(MaxAbsScalerPartitionData::getMaxAbs,
                (a, b) -> {
                    if (a == null)
                        return b;

                    if (b == null)
                        return a;

                    double[] res = new double[a.length];

                    for (int i = 0; i < res.length; i++)
                        res[i] = Math.max(Math.abs(a[i]), Math.abs(b[i]));

                    return res;
                });
            return new MaxAbsScalerPreprocessor<>(maxAbs, basePreprocessor);
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
