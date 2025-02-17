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

package org.apache.ignite.tests.p2p;

import java.util.List;
import java.util.Map;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.cluster.ClusterNode;
import org.apache.ignite.compute.ComputeJob;
import org.apache.ignite.compute.ComputeJobAdapter;
import org.apache.ignite.compute.ComputeJobResult;
import org.apache.ignite.compute.ComputeTaskAdapter;
import org.apache.ignite.internal.util.typedef.F;
import org.apache.ignite.internal.util.typedef.X;
import org.apache.ignite.resources.IgniteInstanceResource;
import org.jetbrains.annotations.Nullable;

/**
 * Test task for {@code GridCacheDeploymentSelfTest}.
 */
public class CacheDeploymentTestTask1 extends ComputeTaskAdapter<ClusterNode, Object> {
    /** Number of puts. */
    private static final int PUT_CNT = 100;

    /** {@inheritDoc} */
    @Override public Map<? extends ComputeJob, ClusterNode> map(List<ClusterNode> subgrid,
        @Nullable ClusterNode node) {
        return F.asMap(
            new ComputeJobAdapter() {
                @IgniteInstanceResource
                private Ignite ignite;

                @Override public Object execute() {
                    X.println("Executing CacheDeploymentTestTask1 job on node " +
                        ignite.cluster().localNode().id());

                    IgniteCache<String, CacheDeploymentTestValue> cache = ignite.cache("default");

                    for (int i = 0; i < PUT_CNT; i++)
                        cache.put("1" + i, new CacheDeploymentTestValue());

                    return null;
                }
            },
            node
        );
    }

    /** {@inheritDoc} */
    @Nullable @Override public Object reduce(List<ComputeJobResult> results) {
        return null;
    }
}