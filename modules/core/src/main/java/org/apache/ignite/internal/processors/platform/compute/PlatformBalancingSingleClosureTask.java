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

package org.apache.ignite.internal.processors.platform.compute;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.ignite.cluster.ClusterNode;
import org.apache.ignite.compute.ComputeJob;
import org.apache.ignite.compute.ComputeLoadBalancer;
import org.apache.ignite.compute.ComputeTaskNoResultCache;
import org.apache.ignite.internal.processors.platform.PlatformContext;
import org.apache.ignite.internal.util.typedef.F;
import org.apache.ignite.resources.LoadBalancerResource;
import org.jetbrains.annotations.Nullable;

/**
 * Interop single-closure task with node balancing.
 */
@ComputeTaskNoResultCache
public class PlatformBalancingSingleClosureTask extends PlatformAbstractTask {
    /** */
    private static final long serialVersionUID = 0L;

    /** Jobs. */
    private PlatformJob job;

    /** Load balancer. */
    @LoadBalancerResource
    private ComputeLoadBalancer lb;

    /**
     * Constructor.
     *
     * @param ctx Platform context.
     * @param taskPtr Task pointer.
     */
    public PlatformBalancingSingleClosureTask(PlatformContext ctx, long taskPtr) {
        super(ctx, taskPtr);
    }

    /** {@inheritDoc} */
    @Nullable @Override public Map<? extends ComputeJob, ClusterNode> map(List<ClusterNode> subgrid,
        @Nullable Object arg) {
        assert job != null : "Job null-check must be performed in native platform.";

        if (!F.isEmpty(subgrid)) {
            Map<ComputeJob, ClusterNode> map = new HashMap<>(1, 1);

            map.put(job, lb.getBalancedNode(job, null));

            return map;
        }
        else
            return Collections.emptyMap();
    }

    /**
     * @param job Job.
     */
    public void job(PlatformJob job) {
        this.job = job;
    }
}