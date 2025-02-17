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

package org.apache.ignite.loadtests.direct.newnodes;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.ignite.cluster.ClusterNode;
import org.apache.ignite.compute.ComputeJob;
import org.apache.ignite.compute.ComputeJobAdapter;
import org.apache.ignite.compute.ComputeJobResult;
import org.apache.ignite.compute.ComputeLoadBalancer;
import org.apache.ignite.compute.ComputeTaskAdapter;
import org.apache.ignite.compute.ComputeTaskSession;
import org.apache.ignite.resources.LoadBalancerResource;
import org.apache.ignite.resources.TaskSessionResource;

/**
 * Single split on new nodes test task.
 */
public class GridSingleSplitNewNodesTestTask extends ComputeTaskAdapter<Integer, Integer> {
    /** */
    @TaskSessionResource
    private ComputeTaskSession taskSes;

    /** */
    @LoadBalancerResource
    private ComputeLoadBalancer balancer;

    /** {@inheritDoc} */
    @Override public Map<? extends ComputeJob, ClusterNode> map(List<ClusterNode> subgrid, Integer arg) {
        assert !subgrid.isEmpty() : "Subgrid cannot be empty.";

        Map<ComputeJobAdapter, ClusterNode> jobs = new HashMap<>(subgrid.size());

        taskSes.setAttribute("1st", "1");
        taskSes.setAttribute("2nd", "2");

        Collection<UUID> assigned = new ArrayList<>(subgrid.size());

        for (int i = 0; i < arg; i++) {
            ComputeJobAdapter job = new ComputeJobAdapter(1) {
                /** */
                @TaskSessionResource
                private ComputeTaskSession jobSes;

                /** {@inheritDoc} */
                @Override public Serializable execute() {
                    assert jobSes != null;

                    Integer arg = this.<Integer>argument(0);

                    assert arg != null;

                    return new GridSingleSplitNewNodesTestJobTarget().executeLoadTestJob(arg, jobSes);
                }
            };

            ClusterNode node = balancer.getBalancedNode(job, null);

            assert node != null;

            assigned.add(node.id());

            jobs.put(job, node);
        }

        taskSes.setAttribute("nodes", assigned);

        return jobs;
    }

    /** {@inheritDoc} */
    @Override public Integer reduce(List<ComputeJobResult> results) {
        int retVal = 0;

        for (ComputeJobResult res : results) {
            assert res.getData() != null : "Load test should return result: " + res;

            retVal += (Integer)res.getData();
        }

        return retVal;
    }
}