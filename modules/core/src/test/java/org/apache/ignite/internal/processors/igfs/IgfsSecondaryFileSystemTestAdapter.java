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

import org.apache.ignite.internal.util.typedef.T2;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;

/**
 * Universal interface to an underlying filesystem.
 * Typically used for secondary filesystem.
 * To be used solely in tests.
 */
public interface IgfsSecondaryFileSystemTestAdapter {
    /**
     * Gets name of the FS.
     * @return name of this file system.
     * @throws IOException in case of failure.
     */
    String name() throws IOException;

    /**
     * Answers if a file denoted by path exists.
     * @param path path of the file to check.
     * @return if the file exists.
     * @throws IOException in case of failure.
     */
    boolean exists(String path) throws IOException;

    /**
     * Deletes a file or directory.
     * @param path the path to delete.
     * @param recursive instructs to delete a directory recursively.
     * @return true on success, false otherwise.
     * @throws IOException On failure.
     */
    boolean delete(String path, boolean recursive) throws IOException;

    /**
     * Makes directories, creating missing parent directories as needed.
     * @param path the directory to create.
     * @throws IOException On failure.
     */
    void mkdirs(String path) throws IOException;

    /**
     * Clears (formats) entire the filesystem.
     * All the data in the filesystem are DESTROYED.
     * @throws IOException On failure.
     */
    void format() throws IOException;

    /**
     * Gets properties (such as owner, group, and permissions) of a file.
     * @param path the path to the file to get properties of.
     * @return the properties.
     */
    Map<String,String> properties(String path) throws IOException;

    /**
     * Get permissions.
     *
     * @param path Path.
     * @return Permissions.
     * @throws IOException If failed.
     */
    String permissions(String path) throws IOException;

    /**
     * Opens input stream to read file contents.
     * @param path the path to the file.
     */
    InputStream openInputStream(String path) throws IOException;

    /**
     * Opens output stream to write file contents.
     * @param path the path to the file to be written.
     * @param append if to append to the end of existing data.
     * @return the OutputStream to write into.
     * @throws IOException On failure.
     */
    OutputStream openOutputStream(String path, boolean append) throws IOException;

    /**
     * Get times for path.
     *
     * @param path Path.
     * @return Times for path (modification time, access time).
     * @throws IOException If failed.
     */
    T2<Long, Long> times(String path) throws IOException;

    /**
     * Get underlying IGFS if it is possible.
     *
     * @return Underlying IGFS or null.
     */
    @Nullable IgfsEx igfs();
}