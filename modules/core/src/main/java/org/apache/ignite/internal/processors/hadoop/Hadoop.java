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

package org.apache.ignite.internal.processors.hadoop;

import org.apache.ignite.IgniteCheckedException;
import org.apache.ignite.configuration.HadoopConfiguration;
import org.apache.ignite.internal.IgniteInternalFuture;
import org.apache.ignite.internal.processors.hadoop.counter.HadoopCounters;
import org.jetbrains.annotations.Nullable;

/**
 * Hadoop facade providing access to Ignite Hadoop features.
 */
public interface Hadoop {
    /**
     * Gets Hadoop module configuration.
     *
     * @return Hadoop module configuration.
     */
    public HadoopConfiguration configuration();

    /**
     * Generate next job ID.
     *
     * @return Next job ID.
     */
    public HadoopJobId nextJobId();

    /**
     * Submits job to job tracker.
     *
     * @param jobId Job ID to submit.
     * @param jobInfo Job info to submit.
     * @return Execution future.
     */
    public IgniteInternalFuture<?> submit(HadoopJobId jobId, HadoopJobInfo jobInfo);

    /**
     * Gets Hadoop job execution status.
     *
     * @param jobId Job ID to get status for.
     * @return Job execution status or {@code null} in case job with the given ID is not found.
     * @throws IgniteCheckedException If failed.
     */
    @Nullable public HadoopJobStatus status(HadoopJobId jobId) throws IgniteCheckedException;

    /**
     * Returns job counters.
     *
     * @param jobId Job ID to get counters for.
     * @return Job counters object.
     * @throws IgniteCheckedException If failed.
     */
    public HadoopCounters counters(HadoopJobId jobId) throws IgniteCheckedException;

    /**
     * Gets Hadoop finish future for particular job.
     *
     * @param jobId Job ID.
     * @return Job finish future or {@code null} in case job with the given ID is not found.
     * @throws IgniteCheckedException If failed.
     */
    @Nullable public IgniteInternalFuture<?> finishFuture(HadoopJobId jobId) throws IgniteCheckedException;

    /**
     * Kills job.
     *
     * @param jobId Job ID.
     * @return {@code True} if job was killed.
     * @throws IgniteCheckedException If failed.
     */
    public boolean kill(HadoopJobId jobId) throws IgniteCheckedException;
}