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

import org.apache.ignite.internal.util.typedef.internal.S;
import org.jetbrains.annotations.Nullable;

/**
 * Result of deletion in IGFS.
 */
public class IgfsDeleteResult {
    /** Success flag. */
    private final boolean success;

    /** Entry info. */
    private final IgfsEntryInfo info;

    /**
     * Constructor.
     *
     * @param success Success flag.
     * @param info Entry info.
     */
    public IgfsDeleteResult(boolean success, @Nullable IgfsEntryInfo info) {
        this.success = success;
        this.info = info;
    }

    /**
     * @return Success flag.
     */
    public boolean success() {
        return success;
    }

    /**
     * @return Entry info.
     */
    public IgfsEntryInfo info() {
        return info;
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(IgfsDeleteResult.class, this);
    }
}
