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

package org.apache.ignite.internal.processors.cache.persistence.pagemem;

import org.apache.ignite.IgniteCheckedException;
import org.apache.ignite.internal.GridKernalContext;
import org.apache.ignite.internal.pagemem.wal.IgniteWriteAheadLogManager;
import org.apache.ignite.internal.pagemem.wal.WALIterator;
import org.apache.ignite.internal.pagemem.wal.WALPointer;
import org.apache.ignite.internal.pagemem.wal.record.RolloverType;
import org.apache.ignite.internal.pagemem.wal.record.WALRecord;
import org.apache.ignite.internal.processors.cache.GridCacheSharedContext;
import org.apache.ignite.internal.processors.cache.persistence.StorageException;
import org.apache.ignite.lang.IgniteBiPredicate;
import org.apache.ignite.lang.IgniteFuture;
import org.jetbrains.annotations.Nullable;

/**
 *
 */
public class NoOpWALManager implements IgniteWriteAheadLogManager {
    /** {@inheritDoc} */
    @Override public boolean isAlwaysWriteFullPages() {
        return true;
    }

    /** {@inheritDoc} */
    @Override public boolean isFullSync() {
        return false;
    }

    /** {@inheritDoc} */
    @Override public int serializerVersion() {
        return 0;
    }

    /** {@inheritDoc} */
    @Override public void resumeLogging(WALPointer ptr) throws IgniteCheckedException {
        // No-op.
    }

    /** {@inheritDoc} */
    @Override public WALPointer log(WALRecord entry) throws IgniteCheckedException, StorageException {
        return null;
    }

    /** {@inheritDoc} */
    @Override public WALPointer log(WALRecord entry, RolloverType rollOverType) {
        return null;
    }

    /** {@inheritDoc} */
    @Override public void flush(WALPointer ptr, boolean explicitFsync) throws IgniteCheckedException, StorageException {

    }

    /** {@inheritDoc} */
    @Override public WALRecord read(WALPointer ptr) throws IgniteCheckedException, StorageException {
        return null;
    }

    /** {@inheritDoc} */
    @Override public WALIterator replay(WALPointer start) throws IgniteCheckedException, StorageException {
        return null;
    }

    /** {@inheritDoc} */
    @Override public WALIterator replay(WALPointer start, @Nullable IgniteBiPredicate<WALRecord.RecordType, WALPointer> recordDeserializeFilter) throws IgniteCheckedException, StorageException {
        return null;
    }

    /** {@inheritDoc} */
    @Override public boolean reserve(WALPointer start) {
        return false;
    }

    /** {@inheritDoc} */
    @Override public void release(WALPointer start) throws IgniteCheckedException {
        // No-op.
    }

    /** {@inheritDoc} */
    @Override public int truncate(WALPointer low, WALPointer high) {
        return 0;
    }

    /** {@inheritDoc} */
    @Override public void notchLastCheckpointPtr(WALPointer ptr) {
        // No-op.
    }

    /** {@inheritDoc} */
    @Override public boolean reserved(WALPointer ptr) {
        return false;
    }

    /** {@inheritDoc} */
    @Override public int reserved(WALPointer low, WALPointer high) {
        return 0;
    }

    /** {@inheritDoc} */
    @Override public boolean disabled(int grpId) {
        return false;
    }

    /** {@inheritDoc} */
    @Override public void start(GridCacheSharedContext cctx) throws IgniteCheckedException {
        // No-op.
    }

    /** {@inheritDoc} */
    @Override public void stop(boolean cancel) {
        // No-op.
    }

    /** {@inheritDoc} */
    @Override public void onKernalStop(boolean cancel) {
        // No-op.
    }

    /** {@inheritDoc} */
    @Override public void onDisconnected(IgniteFuture reconnectFut) {
        // No-op.
    }

    /** {@inheritDoc} */
    @Override public void onReconnected(boolean active) {
        // No-op.
    }

    /** {@inheritDoc} */
    @Override public void printMemoryStats() {
        // No-op.
    }

    /** {@inheritDoc} */
    @Override public void onActivate(GridKernalContext kctx) {
        // No-op.
    }

    /** {@inheritDoc} */
    @Override public void onDeActivate(GridKernalContext kctx) {
        // No-op.
    }

    /** {@inheritDoc} */
    @Override public int walArchiveSegments() {
        return 0;
    }

    /** {@inheritDoc} */
    @Override public long lastArchivedSegment() {
        return -1L;
    }

    /** {@inheritDoc} */
    @Override public long lastCompactedSegment() {
        return -1L;
    }

    /** {@inheritDoc} */
    @Override public long maxArchivedSegmentToDelete() {
        return -1;
    }
}
