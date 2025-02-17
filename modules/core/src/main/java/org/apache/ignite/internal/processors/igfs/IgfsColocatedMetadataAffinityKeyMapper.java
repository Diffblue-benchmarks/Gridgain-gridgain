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

package org.apache.ignite.internal.processors.igfs;

import org.apache.ignite.cache.affinity.AffinityKeyMapper;
import org.apache.ignite.internal.util.typedef.internal.S;

/**
 * Special implementation of affinity mapper which maps all metadata to the same primary.
 */
public class IgfsColocatedMetadataAffinityKeyMapper implements AffinityKeyMapper {
    /** */
    private static final long serialVersionUID = 0L;

    /** Affinity. */
    private static final Integer AFF = 1;

    /** {@inheritDoc} */
    @Override public Object affinityKey(Object key) {
        return AFF;
    }

    /** {@inheritDoc} */
    @Override public void reset() {
        // No-op.
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(IgfsColocatedMetadataAffinityKeyMapper.class, this);
    }
}
