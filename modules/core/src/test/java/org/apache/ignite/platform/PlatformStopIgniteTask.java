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

import org.apache.ignite.IgniteException;
import org.apache.ignite.Ignition;
import org.apache.ignite.cluster.ClusterNode;
import org.apache.ignite.compute.ComputeJob;
import org.apache.ignite.compute.ComputeJobAdapter;
import org.apache.ignite.compute.ComputeJobResult;
import org.apache.ignite.compute.ComputeTaskAdapter;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Task to stop an Ignite node.
 */
public class PlatformStopIgniteTask extends ComputeTaskAdapter<String, Boolean> {
    /** {@inheritDoc} */
    @Nullable @Override public Map<? extends ComputeJob, ClusterNode> map(List<ClusterNode> subgrid,
        @Nullable String arg) throws IgniteException {
        ClusterNode node = subgrid.get(0);

        for (ClusterNode n : subgrid) {
            if (n.isLocal()) {
                node = n;
                break;
            }
        }

        return Collections.singletonMap(new PlatformStopIgniteJob(arg), node);
    }

    /** {@inheritDoc} */
    @Nullable @Override public Boolean reduce(List<ComputeJobResult> results) throws IgniteException {
        ComputeJobResult res = results.get(0);

        if (res.getException() != null)
            throw res.getException();
        else
            return results.get(0).getData();
    }

    /**
     * Job.
     */
    private static class PlatformStopIgniteJob extends ComputeJobAdapter {
        /** */
        private final String igniteInstanceName;

        /**
         * Ctor.
         *
         * @param igniteInstanceName Name.
         */
        private PlatformStopIgniteJob(String igniteInstanceName) {
            this.igniteInstanceName = igniteInstanceName;
        }

        /** {@inheritDoc} */
        @Override public Object execute() throws IgniteException {
            return Ignition.stop(igniteInstanceName, true);
        }
    }
}
