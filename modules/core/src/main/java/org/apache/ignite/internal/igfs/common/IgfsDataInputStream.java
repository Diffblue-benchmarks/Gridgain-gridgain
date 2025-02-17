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

package org.apache.ignite.internal.igfs.common;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInput;

/**
 * Data input stream implementing object input but throwing exceptions on object methods.
 */
public class IgfsDataInputStream extends DataInputStream implements ObjectInput {
    /**
     * Creates a DataInputStream that uses the specified
     * underlying InputStream.
     *
     * @param  in The specified input stream
     */
    public IgfsDataInputStream(InputStream in) {
        super(in);
    }

    /** {@inheritDoc} */
    @Override public Object readObject() throws ClassNotFoundException, IOException {
        throw new IOException("This method must not be invoked on IGFS data input stream.");
    }
}