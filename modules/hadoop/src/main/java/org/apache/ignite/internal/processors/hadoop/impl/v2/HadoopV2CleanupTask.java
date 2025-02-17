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

package org.apache.ignite.internal.processors.hadoop.impl.v2;

import org.apache.hadoop.mapred.JobContextImpl;
import org.apache.hadoop.mapreduce.JobStatus;
import org.apache.hadoop.mapreduce.OutputCommitter;
import org.apache.hadoop.mapreduce.OutputFormat;
import org.apache.ignite.IgniteCheckedException;
import org.apache.ignite.internal.IgniteInterruptedCheckedException;
import org.apache.ignite.internal.processors.hadoop.HadoopTaskInfo;

import java.io.IOException;

/**
 * Hadoop cleanup task (commits or aborts job).
 */
public class HadoopV2CleanupTask extends HadoopV2Task {
    /** Abort flag. */
    private final boolean abort;

    /**
     * @param taskInfo Task info.
     * @param abort Abort flag.
     */
    public HadoopV2CleanupTask(HadoopTaskInfo taskInfo, boolean abort) {
        super(taskInfo);

        this.abort = abort;
    }

    /** {@inheritDoc} */
    @Override public void run0(HadoopV2TaskContext taskCtx) throws IgniteCheckedException {
        JobContextImpl jobCtx = taskCtx.jobContext();

        try {
            OutputFormat outputFormat = getOutputFormat(jobCtx);

            OutputCommitter committer = outputFormat.getOutputCommitter(hadoopContext());

            if (committer != null) {
                if (abort)
                    committer.abortJob(jobCtx, JobStatus.State.FAILED);
                else
                    committer.commitJob(jobCtx);
            }
        }
        catch (ClassNotFoundException | IOException e) {
            throw new IgniteCheckedException(e);
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();

            throw new IgniteInterruptedCheckedException(e);
        }
    }
}