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

package org.apache.ignite.internal.processors.hadoop.impl.fs;

import java.io.IOException;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.FsConstants;
import org.jetbrains.annotations.Nullable;

/**
 * Utilities for configuring file systems to support the separate working directory per each thread.
 */
public class HadoopFileSystemsUtils {
    /** Name of the property for setting working directory on create new local FS instance. */
    public static final String LOC_FS_WORK_DIR_PROP = "fs." + FsConstants.LOCAL_FS_URI.getScheme() + ".workDir";

    /**
     * Setup wrappers of filesystems to support the separate working directory.
     *
     * @param cfg Config for setup.
     */
    public static void setupFileSystems(Configuration cfg) {
        cfg.set("fs." + FsConstants.LOCAL_FS_URI.getScheme() + ".impl", HadoopLocalFileSystemV1.class.getName());
        cfg.set("fs.AbstractFileSystem." + FsConstants.LOCAL_FS_URI.getScheme() + ".impl",
                HadoopLocalFileSystemV2.class.getName());
    }

    /**
     * Gets the property name to disable file system cache.
     * @param scheme The file system URI scheme.
     * @return The property name. If scheme is null,
     * returns "fs.null.impl.disable.cache".
     */
    public static String disableFsCachePropertyName(@Nullable String scheme) {
        return String.format("fs.%s.impl.disable.cache", scheme);
    }

    /**
     * Clears Hadoop {@link FileSystem} cache.
     *
     * @throws IOException On error.
     */
    public static void clearFileSystemCache() throws IOException {
        FileSystem.closeAll();
    }
}