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

package org.apache.ignite.internal.processors.cache.distributed.dht.preloader;

import java.util.concurrent.ConcurrentHashMap;
import org.apache.ignite.cluster.ClusterNode;
import org.apache.ignite.internal.processors.affinity.AffinityTopologyVersion;
import org.apache.ignite.internal.processors.cache.distributed.dht.topology.GridDhtPartitionTopologyImpl;
import org.apache.ignite.internal.util.typedef.internal.S;

/**
 * Partition to node assignments.
 */
public class GridDhtPreloaderAssignments extends ConcurrentHashMap<ClusterNode, GridDhtPartitionDemandMessage> {
    /** */
    private static final long serialVersionUID = 0L;

    /** */
    private final GridDhtPartitionExchangeId exchangeId;

    /** */
    private final AffinityTopologyVersion topVer;

    /** */
    private boolean cancelled;

    /**
     * @param exchangeId Exchange ID.
     * @param topVer Last join order.
     */
    public GridDhtPreloaderAssignments(GridDhtPartitionExchangeId exchangeId, AffinityTopologyVersion topVer) {
        assert exchangeId != null;
        assert topVer.topologyVersion() > 0 : topVer;

        this.exchangeId = exchangeId;
        this.topVer = topVer;
    }

    /**
     * @return {@code True} if assignments creation was cancelled.
     */
    public boolean cancelled() {
        return cancelled;
    }

    /**
     * @param cancelled {@code True} if assignments creation was cancelled.
     */
    public void cancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    /**
     * @return Exchange future.
     */
    GridDhtPartitionExchangeId exchangeId() {
        return exchangeId;
    }

    /**
     * @return Topology version based on last {@link GridDhtPartitionTopologyImpl#readyTopVer}.
     */
    public AffinityTopologyVersion topologyVersion() {
        return topVer;
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridDhtPreloaderAssignments.class, this, "exchId", exchangeId,
            "super", super.toString());
    }
}
