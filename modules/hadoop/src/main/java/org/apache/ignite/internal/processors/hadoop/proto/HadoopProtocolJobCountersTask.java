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

package org.apache.ignite.internal.processors.hadoop.proto;

import java.util.UUID;
import org.apache.ignite.IgniteCheckedException;
import org.apache.ignite.compute.ComputeJobContext;
import org.apache.ignite.internal.processors.hadoop.Hadoop;
import org.apache.ignite.internal.processors.hadoop.HadoopJobId;
import org.apache.ignite.internal.processors.hadoop.counter.HadoopCounters;

/**
 * Task to get job counters.
 */
public class HadoopProtocolJobCountersTask extends HadoopProtocolTaskAdapter<HadoopCounters> {
    /** */
    private static final long serialVersionUID = 0L;

    /** {@inheritDoc} */
    @Override public HadoopCounters run(ComputeJobContext jobCtx, Hadoop hadoop,
        HadoopProtocolTaskArguments args) throws IgniteCheckedException {

        UUID nodeId = UUID.fromString(args.<String>get(0));
        Integer id = args.get(1);

        assert nodeId != null;
        assert id != null;

        return hadoop.counters(new HadoopJobId(nodeId, id));
    }
}