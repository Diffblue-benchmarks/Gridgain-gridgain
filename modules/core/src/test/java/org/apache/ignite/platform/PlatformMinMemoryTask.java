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

package org.apache.ignite.platform;

import org.apache.ignite.cluster.ClusterNode;
import org.apache.ignite.compute.ComputeJob;
import org.apache.ignite.compute.ComputeJobAdapter;
import org.apache.ignite.compute.ComputeJobResult;
import org.apache.ignite.compute.ComputeTaskAdapter;
import org.apache.ignite.internal.util.typedef.F;
import org.jetbrains.annotations.Nullable;

import java.lang.management.ManagementFactory;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Task to get min Java heap memory for node.
 */
public class PlatformMinMemoryTask extends ComputeTaskAdapter<Object, Long> {
    /** {@inheritDoc} */
    @Nullable @Override public Map<? extends ComputeJob, ClusterNode> map(List<ClusterNode> subgrid,
        @Nullable Object arg) {
        return Collections.singletonMap(new MinMemoryJob(), F.first(subgrid));
    }

    /** {@inheritDoc} */
    @Nullable @Override public Long reduce(List<ComputeJobResult> results) {
        return results.get(0).getData();
    }

    /**
     * Job.
     */
    private static class MinMemoryJob extends ComputeJobAdapter {
        /** {@inheritDoc} */
        @Nullable @Override public Object execute() {
            return ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getInit();
        }
    }
}