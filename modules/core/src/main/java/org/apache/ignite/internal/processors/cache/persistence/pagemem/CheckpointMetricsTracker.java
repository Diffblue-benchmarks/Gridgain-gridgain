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

import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import org.apache.ignite.internal.pagemem.wal.record.CheckpointRecord;

/**
 * Tracks various checkpoint phases and stats.
 *
 * Assumed sequence of events:
 * <ol>
 *     <li>Checkpoint start</li>
 *     <li>CP Lock wait start</li>
 *     <li>CP mark start</li>
 *     <li>CP Lock release</li>
 *     <li>Pages write start</li>
 *     <li>fsync start</li>
 *     <li>Checkpoint end</li>
 * </ol>
 */
public class CheckpointMetricsTracker {
    /** */
    private static final AtomicIntegerFieldUpdater<CheckpointMetricsTracker> DATA_PAGES_UPDATER =
        AtomicIntegerFieldUpdater.newUpdater(CheckpointMetricsTracker.class, "dataPages");

    /** */
    private static final AtomicIntegerFieldUpdater<CheckpointMetricsTracker> COW_PAGES_UPDATER =
        AtomicIntegerFieldUpdater.newUpdater(CheckpointMetricsTracker.class, "cowPages");

    /** */
    private volatile int dataPages;

    /** */
    private volatile int cowPages;

    /** */
    private long cpStart = System.currentTimeMillis();

    /** */
    private long cpLockWaitStart;

    /** */
    private long cpMarkStart;

    /** */
    private long cpLockRelease;

    /** */
    private long cpPagesWriteStart;

    /** */
    private long cpFsyncStart;

    /** */
    private long cpEnd;

    /** */
    private long walCpRecordFsyncStart;

    /** */
    private long walCpRecordFsyncEnd;

    /** */
    private long listenersExecEnd;

    /**
     * Increments counter if copy on write page was written.
     */
    public void onCowPageWritten() {
        COW_PAGES_UPDATER.incrementAndGet(this);
    }

    /**
     *
     */
    public void onDataPageWritten() {
        DATA_PAGES_UPDATER.incrementAndGet(this);
    }

    /**
     * @return COW pages.
     */
    public int cowPagesWritten() {
        return cowPages;
    }

    /**
     * @return Data pages written.
     */
    public int dataPagesWritten() {
        return dataPages;
    }

    /**
     *
     */
    public void onLockWaitStart() {
        cpLockWaitStart = System.currentTimeMillis();
    }

    /**
     *
     */
    public void onMarkStart() {
        cpMarkStart = System.currentTimeMillis();
    }

    /**
     *
     */
    public void onLockRelease() {
        cpLockRelease = System.currentTimeMillis();
    }

    /**
     *
     */
    public void onPagesWriteStart() {
        cpPagesWriteStart = System.currentTimeMillis();
    }

    /**
     *
     */
    public void onFsyncStart() {
        cpFsyncStart = System.currentTimeMillis();
    }

    /**
     *
     */
    public void onEnd() {
        cpEnd = System.currentTimeMillis();
    }

    /**
     *
     */
    public void onListenersExecuteEnd() {
        listenersExecEnd = System.currentTimeMillis();
    }

    /**
     *
     */
    public void onWalCpRecordFsyncStart() {
        walCpRecordFsyncStart = System.currentTimeMillis();
    }

    /**
     *
     */
    public void onWalCpRecordFsyncEnd() {
        walCpRecordFsyncEnd = System.currentTimeMillis();
    }

    /**
     * @return Total checkpoint duration.
     */
    public long totalDuration() {
        return cpEnd - cpStart;
    }

    /**
     * @return Checkpoint lock wait duration.
     */
    public long lockWaitDuration() {
        return cpMarkStart - cpLockWaitStart;
    }

    /**
     * @return Checkpoint action before taken write lock duration.
     */
    public long beforeLockDuration() {
        return cpLockWaitStart - cpStart;
    }

    /**
     * @return Execution listeners under write lock duration.
     */
    public long listenersExecuteDuration() {
        return listenersExecEnd - cpMarkStart;
    }

    /**
     * @return Checkpoint mark duration.
     */
    public long markDuration() {
        return cpPagesWriteStart - cpMarkStart;
    }

    /**
     * @return Checkpoint lock hold duration.
     */
    public long lockHoldDuration() {
        return cpLockRelease - cpMarkStart;
    }

    /**
     * @return Pages write duration.
     */
    public long pagesWriteDuration() {
        return cpFsyncStart - cpPagesWriteStart;
    }

    /**
     * @return Checkpoint fsync duration.
     */
    public long fsyncDuration() {
        return cpEnd - cpFsyncStart;
    }

    /**
     * @return Duration of WAL fsync after logging {@link CheckpointRecord} on checkpoint begin.
     */
    public long walCpRecordFsyncDuration() {
        return walCpRecordFsyncEnd - walCpRecordFsyncStart;
    }

    /**
     * @return Checkpoint start time.
     */
    public long checkpointStartTime() {
        return cpStart;
    }
}
