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

package org.apache.ignite.ml.dataset.feature.extractor.impl;

import org.apache.ignite.ml.dataset.feature.extractor.Vectorizer;
import org.apache.ignite.ml.structures.LabeledVector;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Vectorizer on LabeledVector.
 *
 * @param <K> Type of key.
 */
public class LabeledDummyVectorizer<K, L> extends Vectorizer<K, LabeledVector<L>, Integer, L> {
    /** Serial version uid. */
    private static final long serialVersionUID = -6225354615212148224L;

    /**
     * Creates an instance of Vectorizer.
     *
     * @param coords Coordinates.
     */
    public LabeledDummyVectorizer(Integer ... coords) {
        super(coords);
        labeled(-1);
    }

    /** {@inheritDoc} */
    @Override protected Double feature(Integer coord, K key, LabeledVector<L> value) {
        return value.features().get(coord);
    }

    /** {@inheritDoc} */
    @Override protected L label(Integer coord, K key, LabeledVector<L> value) {
        return value.label();
    }

    /** {@inheritDoc} */
    @Override protected L zero() {
        return null;
    }

    /** {@inheritDoc} */
    @Override protected List<Integer> allCoords(K key, LabeledVector<L> value) {
        return IntStream.range(0, value.features().size()).boxed().collect(Collectors.toList());
    }
}
