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

package org.apache.ignite.spi.failover.jobstealing;

import org.apache.ignite.mxbean.MXBeanDescription;
import org.apache.ignite.spi.IgniteSpiManagementMBean;

/**
 * Management bean for {@link JobStealingFailoverSpi}.
 */
@MXBeanDescription("MBean that provides access to job stealing failover SPI configuration.")
public interface JobStealingFailoverSpiMBean extends IgniteSpiManagementMBean {
    /**
     * Gets maximum number of attempts to execute a failed job on another node.
     * If job gets stolen and thief node exists then it is not considered as
     * failed job.
     * If not specified, {@link JobStealingFailoverSpi#DFLT_MAX_FAILOVER_ATTEMPTS} value will be used.
     *
     * @return Maximum number of attempts to execute a failed job on another node.
     */
    @MXBeanDescription("Maximum number of attempts to execute a failed job on another node.")
    public int getMaximumFailoverAttempts();

    /**
     * Get total number of jobs that were failed over including stolen ones.
     *
     * @return Total number of failed over jobs.
     */
    @MXBeanDescription("Total number of jobs that were failed over including stolen ones.")
    public int getTotalFailedOverJobsCount();

    /**
     * Get total number of jobs that were stolen.
     *
     * @return Total number of stolen jobs.
     */
    @MXBeanDescription("Total number of jobs that were stolen.")
    public int getTotalStolenJobsCount();
}