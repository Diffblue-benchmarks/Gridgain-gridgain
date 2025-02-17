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

package org.apache.ignite.internal.pagemem;

import org.apache.ignite.IgniteCheckedException;
import org.apache.ignite.internal.stat.IoStatisticsHolder;

/**
 * Supports operations on pages.
 */
public interface PageSupport {
    /**
     * Gets the page absolute pointer associated with the given page ID. Each page obtained with this method must be
     * released by calling {@link #releasePage(int, long, long)}. This method will allocate page with given ID if it doesn't
     * exist.
     *
     * @param grpId Cache group ID.
     * @param pageId Page ID.
     * @return Page pointer.
     * @throws IgniteCheckedException If failed.
     */
    public long acquirePage(int grpId, long pageId) throws IgniteCheckedException;

    /**
     * Gets the page absolute pointer associated with the given page ID. Each page obtained with this method must be
     * released by calling {@link #releasePage(int, long, long)}. This method will allocate page with given ID if it
     * doesn't exist.
     *
     * @param grpId Cache group ID.
     * @param pageId Page ID.
     * @param statHolder Statistics holder to track IO operations.
     * @return Page pointer.
     * @throws IgniteCheckedException If failed.
     */
    public long acquirePage(int grpId, long pageId, IoStatisticsHolder statHolder) throws IgniteCheckedException;

    /**
     *
     * @param grpId Cache group ID.
     * @param pageId Page ID to release.
     * @param page Page pointer.
     */
    public void releasePage(int grpId, long pageId, long page);

    /**
     *
     * @param grpId Cache group ID.
     * @param pageId Page ID.
     * @param page Page pointer.
     * @return Pointer for reading the page.
     */
    public long readLock(int grpId, long pageId, long page);

    /**
     * Obtains read lock without checking page tag.
     *
     * @param grpId Cache group ID.
     * @param pageId Page ID.
     * @param page Page pointer.
     * @return Pointer for reading the page.
     */
    public long readLockForce(int grpId, long pageId, long page);

    /**
     * Releases locked page.
     *
     * @param grpId Cache group ID.
     * @param pageId Page ID.
     * @param page Page pointer.
     */
    public void readUnlock(int grpId, long pageId, long page);

    /**
     *
     * @param grpId Cache group ID.
     * @param pageId Page ID.
     * @param page Page pointer.
     * @return Address of a buffer with contents of the given page or
     *            {@code 0L} if attempt to take the write lock failed.
     */
    public long writeLock(int grpId, long pageId, long page);

    /**
     *
     * @param grpId Cache group ID.
     * @param pageId Page ID.
     * @param page Page pointer.
     * @return Address of a buffer with contents of the given page or
     *            {@code 0L} if attempt to take the write lock failed.
     */
    public long tryWriteLock(int grpId, long pageId, long page);

    /**
     * Releases locked page.
     *
     * @param grpId Cache group ID.
     * @param pageId Page ID.
     * @param page Page pointer.
     * @param walPlc {@code True} if page should be recorded to WAL, {@code false} if the page must not
     *      be recorded and {@code null} for the default behavior.
     * @param dirtyFlag Determines whether the page was modified since the last checkpoint.
     */
    public void writeUnlock(int grpId, long pageId, long page, Boolean walPlc,
        boolean dirtyFlag);

    /**
     * @param grpId Cache group ID.
     * @param pageId Page ID.
     * @param page Page pointer.
     * @return {@code True} if the page is dirty.
     */
    public boolean isDirty(int grpId, long pageId, long page);
}
