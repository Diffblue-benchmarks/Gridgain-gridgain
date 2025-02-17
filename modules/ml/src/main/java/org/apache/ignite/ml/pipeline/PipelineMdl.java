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

package org.apache.ignite.ml.pipeline;

import org.apache.ignite.ml.IgniteModel;
import org.apache.ignite.ml.math.primitives.vector.Vector;
import org.apache.ignite.ml.preprocessing.Preprocessor;

/**
 * Wraps the model produced by {@link Pipeline}.
 *
 * @param <K> Type of a key in {@code upstream} data.
 * @param <V> Type of a value in {@code upstream} data.
 */
public class PipelineMdl<K, V> implements IgniteModel<Vector, Double> {
    /** Internal model produced by {@link Pipeline}. */
    private IgniteModel<Vector, Double> internalMdl;

    /** Final preprocessor. */
    private Preprocessor<K, V> preprocessor;

    /** */
    @Override public Double predict(Vector vector) {
        return internalMdl.predict(vector);
    }

    /** */
    public Preprocessor<K, V> getPreprocessor() {
        return preprocessor;
    }

    /** */
    public IgniteModel<Vector, Double> getInternalMdl() {
        return internalMdl;
    }

    /** */
    public PipelineMdl<K, V> withInternalMdl(IgniteModel<Vector, Double> internalMdl) {
        this.internalMdl = internalMdl;
        return this;
    }

    /** */
    public PipelineMdl<K, V> withPreprocessor(Preprocessor<K, V> preprocessor) {
        this.preprocessor = preprocessor;
        return this;
    }

    /** */
    @Override public String toString() {
        return "PipelineMdl{" +
            "internalMdl=" + internalMdl +
            '}';
    }
}
