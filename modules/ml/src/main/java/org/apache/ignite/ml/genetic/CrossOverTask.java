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

package org.apache.ignite.ml.genetic;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteException;
import org.apache.ignite.cache.affinity.Affinity;
import org.apache.ignite.cluster.ClusterNode;
import org.apache.ignite.compute.ComputeJob;
import org.apache.ignite.compute.ComputeJobResult;
import org.apache.ignite.compute.ComputeJobResultPolicy;
import org.apache.ignite.compute.ComputeTaskAdapter;
import org.apache.ignite.ml.genetic.parameter.GAConfiguration;
import org.apache.ignite.ml.genetic.parameter.GAGridConstants;
import org.apache.ignite.resources.IgniteInstanceResource;

/**
 * Responsible for assigning 2 X 'parent' chromosomes to produce 2 X 'child' chromosomes.
 *
 * <p>
 *
 * CrossOverTask leverages  Ignite's data affinity capabilities for routing CrossOverJobs to primary <br/> IgniteNode
 * where 'parent' chromosomes reside.
 *
 * </p>
 */
public class CrossOverTask extends ComputeTaskAdapter<List<Long>, Boolean> {
    /** Ignite instance */
    @IgniteInstanceResource
    private Ignite ignite = null;

    /** GAConfiguration */
    private GAConfiguration cfg;

    /**
     * @param cfg GAConfiguration
     */
    public CrossOverTask(GAConfiguration cfg) {
        this.cfg = cfg;
    }

    /** {@inheritDoc} */
    @Override public Map map(List<ClusterNode> nodes, List<Long> chromosomeKeys) throws IgniteException {

        Map<ComputeJob, ClusterNode> map = new HashMap<>();

        Affinity affinity = ignite.affinity(GAGridConstants.POPULATION_CACHE);

        Map<ClusterNode, Collection<Long>> nodeKeys = affinity.mapKeysToNodes(chromosomeKeys);

        for (Map.Entry<ClusterNode, Collection<Long>> entry : nodeKeys.entrySet()) {
            ClusterNode aNode = entry.getKey();
            map = setupCrossOver(aNode, (List<Long>)entry.getValue(), map);
        }
        return map;
    }

    /** {@inheritDoc} */
    @Override public Boolean reduce(List<ComputeJobResult> list) throws IgniteException {
        // TODO Auto-generated method stub
        return Boolean.TRUE;
    }

    /** {@inheritDoc} */
    @Override public ComputeJobResultPolicy result(ComputeJobResult res, List<ComputeJobResult> rcvd) {
        IgniteException err = res.getException();

        if (err != null)
            return ComputeJobResultPolicy.FAILOVER;

        // If there is no exception, wait for all job results.
        return ComputeJobResultPolicy.WAIT;

    }

    /**
     * Helper method to help assign ComputeJobs to respective ClusterNodes.
     *
     * @param clusterNode Cluster node.
     * @param keys Primary keys of Chromosomes.
     * @param map Nodes where jobs will be sent.
     * @return A map of ComputeJob/ClusterNode's.
     */
    private Map<ComputeJob, ClusterNode> setupCrossOver(ClusterNode clusterNode, List<Long> keys,
        Map<ComputeJob, ClusterNode> map) {
        // Calculate number of Jobs = keys / 2
        // as we desire pairs of Chromosomes to be swapped
        int numOfJobs = keys.size() / 2;
        int k = 0;
        for (int i = 0; i < numOfJobs; i++) {
            Long key1 = keys.get(k);
            Long key2 = keys.get(k + 1);

            CrossOverJob job = new CrossOverJob(key1, key2, this.cfg.getCrossOverRate());
            map.put(job, clusterNode);
            k = k + 2;
        }
        return map;
    }
}
