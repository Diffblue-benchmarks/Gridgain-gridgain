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
import org.apache.ignite.cluster.ClusterNode;
import org.apache.ignite.compute.ComputeJob;
import org.apache.ignite.compute.ComputeJobAdapter;
import org.apache.ignite.compute.ComputeJobResult;
import org.apache.ignite.compute.ComputeTaskAdapter;
import org.apache.ignite.internal.util.typedef.F;
import org.apache.ignite.internal.util.typedef.T2;
import org.apache.ignite.internal.util.typedef.X;
import org.apache.ignite.resources.IgniteInstanceResource;
import org.jetbrains.annotations.Nullable;

/**
 * Test task for {@code GridCacheDeploymentSelfTest}.
 */
public class CacheDeploymentTestTask3 extends ComputeTaskAdapter<T2<ClusterNode, String>, Object> {
    /** {@inheritDoc} */
    @Override public Map<? extends ComputeJob, ClusterNode> map(List<ClusterNode> subgrid,
        @Nullable T2<ClusterNode, String> tup) {
        final String val = tup.getValue();

        return F.asMap(
                new ComputeJobAdapter() {
                    @IgniteInstanceResource
                    private Ignite ignite;

                    @Override public Object execute() {
                        X.println("Executing CacheDeploymentTestTask3 job on node " +
                                ignite.cluster().localNode().id());

                        ignite.<String, CacheDeploymentTestValue>cache("default").put(val,
                                new CacheDeploymentTestValue());

                        return null;
                    }
                },
                tup.get1()
        );
    }

    /** {@inheritDoc} */
    @Nullable @Override public Object reduce(List<ComputeJobResult> results) {
        return null;
    }
}