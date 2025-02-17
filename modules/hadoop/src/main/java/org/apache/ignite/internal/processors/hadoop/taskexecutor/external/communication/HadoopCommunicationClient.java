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

package org.apache.ignite.internal.processors.hadoop.taskexecutor.external.communication;

import org.apache.ignite.IgniteCheckedException;
import org.apache.ignite.internal.processors.hadoop.message.HadoopMessage;
import org.apache.ignite.internal.processors.hadoop.taskexecutor.external.HadoopProcessDescriptor;

/**
 *
 */
public interface HadoopCommunicationClient {
    /**
     * @return {@code True} if client has been closed by this call,
     *      {@code false} if failed to close client (due to concurrent reservation or concurrent close).
     */
    public boolean close();

    /**
     * Forces client close.
     */
    public void forceClose();

    /**
     * @return {@code True} if client is closed;
     */
    public boolean closed();

    /**
     * @return {@code True} if client was reserved, {@code false} otherwise.
     */
    public boolean reserve();

    /**
     * Releases this client by decreasing reservations.
     */
    public void release();

    /**
     * @return {@code True} if client was reserved.
     */
    public boolean reserved();

    /**
     * Gets idle time of this client.
     *
     * @return Idle time of this client.
     */
    public long getIdleTime();

    /**
     * @param desc Process descriptor.
     * @param msg Message to send.
     * @throws IgniteCheckedException If failed.
     */
    public void sendMessage(HadoopProcessDescriptor desc, HadoopMessage msg) throws IgniteCheckedException;
}