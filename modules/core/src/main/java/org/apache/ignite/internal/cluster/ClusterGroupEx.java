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

package org.apache.ignite.internal.cluster;

import java.util.UUID;
import org.apache.ignite.cluster.ClusterGroup;

/**
 * Internal projection interface.
 */
public interface ClusterGroupEx extends ClusterGroup {
    /**
     * Creates projection for specified subject ID.
     *
     * @param subjId Subject ID.
     * @return Cluster group.
     */
    public ClusterGroupEx forSubjectId(UUID subjId);

    /**
     * @param cacheName Cache name.
     * @param affNodes Flag to include affinity nodes.
     * @param nearNodes Flag to include near nodes.
     * @param clientNodes Flag to include client nodes.
     * @return Cluster group.
     */
    public ClusterGroup forCacheNodes(String cacheName, boolean affNodes, boolean nearNodes, boolean clientNodes);

    /**
     * Create projection for IGFS server nodes.
     *
     * @param igfsName IGFS name.
     * @param metaCacheName Metadata cache name.
     * @return Cluster group.
     */
    public ClusterGroup forIgfsMetadataDataNodes(String igfsName, String metaCacheName);
}