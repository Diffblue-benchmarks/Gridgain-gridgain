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

package org.apache.ignite.internal.processors.query;

import java.util.Iterator;
import org.apache.ignite.IgniteCheckedException;
import org.apache.ignite.IgniteException;
import org.apache.ignite.internal.util.lang.GridCloseableIterator;
import org.jetbrains.annotations.NotNull;

/** */
public interface UpdateSourceIterator<T> extends GridCloseableIterator<T> {
    /**
     * @return Operation.
     */
    public EnlistOperation operation();

    /**
     * Callback method which should be called before moving iteration into another thread.
     */
    public default void beforeDetach() {
        // No-op.
    }

    /** {@inheritDoc} */
    @Override default void close() throws IgniteCheckedException {
        // No-op.
    }

    /** {@inheritDoc} */
    @Override default boolean isClosed() {
        return false;
    }

    /** {@inheritDoc} */
    @Override default void removeX() throws IgniteCheckedException {
        throw new UnsupportedOperationException("remove");
    }

    /** {@inheritDoc} */
    @Override default boolean hasNext() {
        try {
            return hasNextX();
        }
        catch (IgniteCheckedException e) {
            throw new IgniteException(e);
        }
    }

    /** {@inheritDoc} */
    @Override default T next() {
        try {
            return nextX();
        }
        catch (IgniteCheckedException e) {
            throw new IgniteException(e);
        }
    }

    /** {@inheritDoc} */
    @Override default void remove() {
        try {
            removeX();
        }
        catch (IgniteCheckedException e) {
            throw new IgniteException(e);
        }
    }

    /** {@inheritDoc} */
    @Override @NotNull default Iterator<T> iterator() {
        return this;
    }
}
