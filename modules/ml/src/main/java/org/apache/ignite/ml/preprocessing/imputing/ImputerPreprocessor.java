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

package org.apache.ignite.ml.preprocessing.imputing;

import org.apache.ignite.ml.math.primitives.vector.Vector;
import org.apache.ignite.ml.preprocessing.Preprocessor;
import org.apache.ignite.ml.structures.LabeledVector;

/**
 * Preprocessing function that makes imputing.
 *
 * @param <K> Type of a key in {@code upstream} data.
 * @param <V> Type of a value in {@code upstream} data.
 */
public class ImputerPreprocessor<K, V> implements Preprocessor<K, V> {
    /** */
    private static final long serialVersionUID = 6887800576392623469L;

    /** Filling values. */
    private final Vector imputingValues;

    /** Base preprocessor. */
    private final Preprocessor<K, V> basePreprocessor;

    /**
     * Constructs a new instance of imputing preprocessor.
     *
     * @param basePreprocessor Base preprocessor.
     */
    public ImputerPreprocessor(Vector imputingValues,
                               Preprocessor<K, V> basePreprocessor) {
        this.imputingValues = imputingValues;
        this.basePreprocessor = basePreprocessor;
    }

    /**
     * Applies this preprocessor.
     *
     * @param k Key.
     * @param v Value.
     * @return Preprocessed row.
     */
    @Override public LabeledVector apply(K k, V v) {
        LabeledVector res = basePreprocessor.apply(k, v);

        assert res.size() == imputingValues.size();

        for (int i = 0; i < res.size(); i++) {
            if (Double.valueOf(res.get(i)).equals(Double.NaN))
                res.set(i, imputingValues.get(i));
        }
        return res;
    }
}
