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

package org.apache.ignite.internal.processors.igfs.secondary.local;

import org.apache.ignite.igfs.secondary.IgfsSecondaryFileSystemPositionedReadable;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;

/**
 * Positioned readable interface for local secondary file system.
 */
public class LocalFileSystemPositionedReadable extends BufferedInputStream
    implements IgfsSecondaryFileSystemPositionedReadable {
    /** Last read position. */
    private long lastReadPos;

    /**
     * Constructor.
     *
     * @param in Input stream.
     * @param bufSize Buffer size.
     */
    public LocalFileSystemPositionedReadable(FileInputStream in, int bufSize) {
        super(in, bufSize);
    }

    /** {@inheritDoc} */
    @Override public int read(long readPos, byte[] buf, int off, int len) throws IOException {
        if (in == null)
            throw new IOException("Stream is closed.");

        if (readPos < lastReadPos || readPos + len > lastReadPos + this.buf.length) {
            ((FileInputStream)in).getChannel().position(readPos);

            pos = 0;
            count = 0;
        }

        int bytesRead = read(buf, off, len);

        if (bytesRead != -1) {
            // Advance last read position only if we really read some bytes from the stream.
            lastReadPos = readPos + bytesRead;
        }

        return bytesRead;
    }
}
