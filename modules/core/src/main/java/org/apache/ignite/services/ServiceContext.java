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

package org.apache.ignite.services;

import java.io.Serializable;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/**
 * Service execution context. Execution context is provided into {@link Service#execute(ServiceContext)}
 * and {@link Service#cancel(ServiceContext)} methods and contains information about specific service
 * execution.
 */
public interface ServiceContext extends Serializable {
    /**
     * Gets service name.
     *
     * @return Service name.
     */
    public String name();

    /**
     * Gets service execution ID. Execution ID is guaranteed to be unique across
     * all service deployments.
     *
     * @return Service execution ID.
     */
    public UUID executionId();

    /**
     * Get flag indicating whether service has been cancelled or not.
     *
     * @return Flag indicating whether service has been cancelled or not.
     */
    public boolean isCancelled();

    /**
     * Gets cache name used for key-to-node affinity calculation. This parameter is optional
     * and is set only when key-affinity service was deployed.
     *
     * @return Cache name, possibly {@code null}.
     */
    @Nullable public String cacheName();

    /**
     * Gets affinity key used for key-to-node affinity calculation. This parameter is optional
     * and is set only when key-affinity service was deployed.
     *
     * @return Affinity key, possibly {@code null}.
     */
    @Nullable public <K> K affinityKey();
}