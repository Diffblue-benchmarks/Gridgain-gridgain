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

package org.apache.ignite.internal.processors.metastorage.persistence;

import java.io.Serializable;
import java.util.function.BiConsumer;

/**
 * Empty metastorage is the specific implementation to be used in in-memory clusters to have distributed metastorage
 * without any data until cluster is activated.
 */
class EmptyDistributedMetaStorageBridge implements DistributedMetaStorageBridge {
    /** {@inheritDoc} */
    @Override public Serializable read(String globalKey, boolean unmarshal) {
        return null;
    }

    /** {@inheritDoc} */
    @Override public void iterate(
        String globalKeyPrefix,
        BiConsumer<String, ? super Serializable> cb,
        boolean unmarshal
    ) {
    }

    /** {@inheritDoc} */
    @Override public void write(String globalKey, byte[] valBytes) {
        throw new UnsupportedOperationException("write");
    }

    /** {@inheritDoc} */
    @Override public void onUpdateMessage(DistributedMetaStorageHistoryItem histItem) {
        throw new UnsupportedOperationException("onUpdateMessage");
    }

    /** {@inheritDoc} */
    @Override public void removeHistoryItem(long ver) {
        throw new UnsupportedOperationException("removeHistoryItem");
    }

    /** {@inheritDoc} */
    @Override public DistributedMetaStorageKeyValuePair[] localFullData() {
        return DistributedMetaStorageKeyValuePair.EMPTY_ARRAY;
    }
}
