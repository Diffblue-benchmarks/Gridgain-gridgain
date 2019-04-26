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

package org.apache.ignite.internal.util.lang.gridfunc;

import java.util.Collection;
import org.apache.ignite.internal.util.typedef.internal.S;
import org.apache.ignite.lang.IgnitePredicate;

/**
 * Predicate that returns {@code true} if its free variable is not contained in given collection.
 *
 * @param <T> Type of the free variable for the predicate and type of the collection elements.
 */
public class NotContainsPredicate<T> implements IgnitePredicate<T> {
    /** */
    private static final long serialVersionUID = 0L;

    /** */
    private final Collection<? extends T> col;

    /**
     * @param col Collection to check for containment.
     */
    public NotContainsPredicate(Collection<? extends T> col) {
        this.col = col;
    }

    /** {@inheritDoc} */
    @Override public boolean apply(T t) {
        assert col != null;

        return !col.contains(t);
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(NotContainsPredicate.class, this);
    }
}
