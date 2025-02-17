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

import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteException;
import org.apache.ignite.Ignition;
import org.apache.ignite.cluster.ClusterNode;
import org.apache.ignite.compute.ComputeJob;
import org.apache.ignite.compute.ComputeJobAdapter;
import org.apache.ignite.compute.ComputeJobResult;
import org.apache.ignite.compute.ComputeTaskAdapter;
import org.apache.ignite.internal.util.typedef.F;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Task to start an Ignite node.
 */
public class PlatformStartIgniteTask extends ComputeTaskAdapter<String, String> {
    /** {@inheritDoc} */
    @Nullable @Override public Map<? extends ComputeJob, ClusterNode> map(List<ClusterNode> subgrid,
        @Nullable String arg) throws IgniteException {
        return Collections.singletonMap(new PlatformStartIgniteJob(arg), F.first(subgrid));
    }

    /** {@inheritDoc} */
    @Nullable @Override public String reduce(List<ComputeJobResult> results) throws IgniteException {
        ComputeJobResult res = results.get(0);

        if (res.getException() != null)
            throw res.getException();
        else
            return results.get(0).getData();
    }

    /**
     * Job.
     */
    private static class PlatformStartIgniteJob extends ComputeJobAdapter {
        /** */
        private final String springConfig;

        /**
         * Ctor.
         *
         * @param springConfig Config.
         */
        private PlatformStartIgniteJob(String springConfig) {
            this.springConfig = springConfig;
        }

        /** {@inheritDoc} */
        @Override public Object execute() throws IgniteException {
            Ignite ignite = Ignition.start(springConfig);

            return ignite.name();
        }
    }
}
