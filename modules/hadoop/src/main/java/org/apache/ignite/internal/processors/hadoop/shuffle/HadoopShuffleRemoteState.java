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

package org.apache.ignite.internal.processors.hadoop.shuffle;

import org.apache.ignite.internal.util.future.GridFutureAdapter;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Remote shuffle state.
 */
class HadoopShuffleRemoteState {
    /** Message count. */
    private final AtomicLong msgCnt = new AtomicLong();

    /** Completion future. */
    private final GridFutureAdapter fut = new GridFutureAdapter();

    /**
     * Callback invoked when shuffle message is sent.
     */
    public void onShuffleMessage() {
        msgCnt.incrementAndGet();
    }

    /**
     * Callback invoked on shuffle finish response.
     */
    public void onShuffleFinishResponse() {
        fut.onDone();
    }

    /**
     * @return Message count.
     */
    public long messageCount() {
        return msgCnt.get();
    }

    /**
     * @return Completion future.
     */
    public GridFutureAdapter future() {
        return fut;
    }
}
