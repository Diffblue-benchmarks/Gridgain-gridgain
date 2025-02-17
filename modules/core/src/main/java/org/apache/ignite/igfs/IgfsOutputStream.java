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

import java.io.DataInput;
import java.io.IOException;
import java.io.OutputStream;

/**
 * {@code IGFS} output stream to write data into the file system.
 */
public abstract class IgfsOutputStream extends OutputStream {
    /**
     * Transfers specified amount of bytes from data input to this output stream.
     * This method is optimized to avoid unnecessary temporal buffer creation and byte array copy.
     *
     * @param in Data input to copy bytes from.
     * @param len Data length to copy.
     * @throws IOException If write failed, read from input failed or there is no enough data in data input.
     */
    public abstract void transferFrom(DataInput in, int len) throws IOException;
}