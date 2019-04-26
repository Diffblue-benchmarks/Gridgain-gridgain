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

package org.apache.ignite.p2p;

import java.io.Serializable;
import org.apache.ignite.internal.util.typedef.internal.S;

/**
 */
public class GridSwapSpaceCustomKey implements Serializable {
    /** */
    private long id = -1;

    /**
     * @return ID.
     */
    public long getId() {
        return id;
    }

    /**
     *
     * @param id ID.
     */
    public void setId(long id) {
        this.id = id;
    }

    /** {@inheritDoc} */
    @Override public boolean equals(Object obj) {
        return obj instanceof GridSwapSpaceCustomKey && ((GridSwapSpaceCustomKey)obj).id == id;
    }

    /** {@inheritDoc} */
    @Override public int hashCode() {
        return Long.valueOf(id).hashCode();
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridSwapSpaceCustomKey.class, this);
    }
}