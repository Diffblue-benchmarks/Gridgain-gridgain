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

package org.apache.ignite.internal.processors.hadoop.impl;

import java.io.IOException;
import java.util.UUID;
import org.apache.hadoop.mapred.JobConf;
import org.apache.ignite.internal.processors.hadoop.HadoopDefaultJobInfo;
import org.apache.ignite.internal.processors.hadoop.HadoopJobEx;
import org.apache.ignite.internal.processors.hadoop.HadoopJobId;
import org.apache.ignite.internal.processors.hadoop.HadoopHelperImpl;
import org.apache.ignite.internal.processors.hadoop.impl.examples.HadoopWordCount1;
import org.apache.ignite.internal.processors.hadoop.impl.v2.HadoopV2Job;

import static org.apache.ignite.internal.processors.hadoop.impl.HadoopUtils.createJobInfo;

/**
 * Tests of Map, Combine and Reduce task executions via running of job of hadoop API v1.
 */
public class HadoopTasksV1Test extends HadoopTasksVersionsAbstractTest {
    /**
     * Creates WordCount hadoop job for API v1.
     *
     * @param inFile Input file name for the job.
     * @param outFile Output file name for the job.
     * @return Hadoop job.
     * @throws IOException If fails.
     */
    @Override public HadoopJobEx getHadoopJob(String inFile, String outFile) throws Exception {
        JobConf jobConf = HadoopWordCount1.getJob(inFile, outFile);

        setupFileSystems(jobConf);

        HadoopDefaultJobInfo jobInfo = createJobInfo(jobConf, null);

        UUID uuid = new UUID(0, 0);

        HadoopJobId jobId = new HadoopJobId(uuid, 0);

        return jobInfo.createJob(HadoopV2Job.class, jobId, log, null, new HadoopHelperImpl());
    }

    /** {@inheritDoc} */
    @Override public String getOutputFileNamePrefix() {
        return "part-";
    }
}
