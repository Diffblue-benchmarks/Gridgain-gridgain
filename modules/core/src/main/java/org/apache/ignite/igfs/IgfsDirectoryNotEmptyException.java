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

package org.apache.ignite.igfs;

import org.jetbrains.annotations.Nullable;

/**
 * Exception indicating that directory can not be deleted because it is not empty.
 */
public class IgfsDirectoryNotEmptyException extends IgfsException {
    /** */
    private static final long serialVersionUID = 0L;

    /**
     * Constructor.
     *
     * @param msg Message.
     */
    public IgfsDirectoryNotEmptyException(String msg) {
        super(msg);
    }

    /**
     * Constructor.
     *
     * @param cause Cause.
     */
    public IgfsDirectoryNotEmptyException(Throwable cause) {
        super(cause);
    }

    /**
     * Constructor.
     *
     * @param msg Message.
     * @param cause Cause.
     */
    public IgfsDirectoryNotEmptyException(@Nullable String msg, @Nullable Throwable cause) {
        super(msg, cause);
    }
}