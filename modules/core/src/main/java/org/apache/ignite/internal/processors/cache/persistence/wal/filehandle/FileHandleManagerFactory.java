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

package org.apache.ignite.internal.processors.cache.persistence.wal.filehandle;

import java.util.function.Supplier;
import org.apache.ignite.IgniteSystemProperties;
import org.apache.ignite.configuration.DataStorageConfiguration;
import org.apache.ignite.configuration.WALMode;
import org.apache.ignite.internal.pagemem.wal.WALPointer;
import org.apache.ignite.internal.processors.cache.GridCacheSharedContext;
import org.apache.ignite.internal.processors.cache.persistence.DataStorageMetricsImpl;
import org.apache.ignite.internal.processors.cache.persistence.wal.serializer.RecordSerializer;

/**
 * Factory of {@link FileHandleManager}.
 */
public class FileHandleManagerFactory {
    /**   */
    private final boolean walFsyncWithDedicatedWorker =
        IgniteSystemProperties.getBoolean(IgniteSystemProperties.IGNITE_WAL_FSYNC_WITH_DEDICATED_WORKER, false);

    /** Data storage configuration. */
    private final DataStorageConfiguration dsConf;

    /**
     * @param conf Data storage configuration.
     */
    public FileHandleManagerFactory(DataStorageConfiguration conf) {
        dsConf = conf;
    }

    /**
     * @param cctx Cache context.
     * @param metrics Data storage metrics.
     * @param mmap Using mmap.
     * @param lastWALPtr Last WAL pointer.
     * @param serializer Serializer.
     * @param currHandleSupplier Supplier of current handle.
     * @return One of implementation of {@link FileHandleManager}.
     */
    public FileHandleManager build(
        GridCacheSharedContext cctx,
        DataStorageMetricsImpl metrics,
        boolean mmap,
        Supplier<WALPointer> lastWALPtr,
        RecordSerializer serializer,
        Supplier<FileWriteHandle> currHandleSupplier
    ) {
        if (dsConf.getWalMode() == WALMode.FSYNC && !walFsyncWithDedicatedWorker)
            return new FsyncFileHandleManagerImpl(
                cctx,
                metrics,
                lastWALPtr,
                serializer,
                currHandleSupplier,
                dsConf.getWalMode(),
                dsConf.getWalSegmentSize(),
                dsConf.getWalFsyncDelayNanos(),
                dsConf.getWalThreadLocalBufferSize()
            );
        else
            return new FileHandleManagerImpl(
                cctx,
                metrics,
                mmap,
                lastWALPtr,
                serializer,
                currHandleSupplier,
                dsConf.getWalMode(),
                dsConf.getWalBufferSize(),
                dsConf.getWalSegmentSize(),
                dsConf.getWalFsyncDelayNanos()
            );
    }
}
