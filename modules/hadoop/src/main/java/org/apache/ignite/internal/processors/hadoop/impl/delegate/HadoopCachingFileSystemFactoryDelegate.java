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

package org.apache.ignite.internal.processors.hadoop.impl.delegate;

import org.apache.hadoop.fs.FileSystem;
import org.apache.ignite.IgniteCheckedException;
import org.apache.ignite.IgniteException;
import org.apache.ignite.hadoop.fs.CachingHadoopFileSystemFactory;
import org.apache.ignite.internal.processors.hadoop.impl.fs.HadoopFileSystemsUtils;
import org.apache.ignite.internal.processors.hadoop.impl.fs.HadoopLazyConcurrentMap;

import java.io.IOException;

/**
 * Caching Hadoop file system factory delegate.
 */
public class HadoopCachingFileSystemFactoryDelegate extends HadoopBasicFileSystemFactoryDelegate {
    /** Per-user file system cache. */
    private final HadoopLazyConcurrentMap<String, FileSystem> cache = new HadoopLazyConcurrentMap<>(
        new HadoopLazyConcurrentMap.ValueFactory<String, FileSystem>() {
            @Override public FileSystem createValue(String key) throws IOException {
                return HadoopCachingFileSystemFactoryDelegate.super.getWithMappedName(key);
            }
        }
    );

    /**
     * Constructor.
     *
     * @param proxy Proxy.
     */
    public HadoopCachingFileSystemFactoryDelegate(CachingHadoopFileSystemFactory proxy) {
        super(proxy);
    }

    /** {@inheritDoc} */
    @Override public FileSystem getWithMappedName(String name) throws IOException {
        return cache.getOrCreate(name);
    }

    /** {@inheritDoc} */
    @Override public void start() throws IgniteException {
        super.start();

        // Disable caching.
        cfg.setBoolean(HadoopFileSystemsUtils.disableFsCachePropertyName(fullUri.getScheme()), true);
    }

    /** {@inheritDoc} */
    @Override public void stop() throws IgniteException {
        super.stop();

        try {
            cache.close();
        }
        catch (IgniteCheckedException ice) {
            throw new IgniteException(ice);
        }
    }
}
