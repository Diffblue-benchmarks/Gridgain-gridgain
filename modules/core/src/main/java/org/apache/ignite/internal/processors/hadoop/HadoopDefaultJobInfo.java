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

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.Map;
import org.apache.ignite.IgniteCheckedException;
import org.apache.ignite.IgniteLogger;
import org.apache.ignite.internal.processors.igfs.IgfsUtils;
import org.apache.ignite.internal.util.typedef.internal.U;
import org.jetbrains.annotations.Nullable;

/**
 * Hadoop job info based on default Hadoop configuration.
 */
public class HadoopDefaultJobInfo implements HadoopJobInfo, Externalizable {
    /** */
    private static final long serialVersionUID = 5489900236464999951L;

    /** {@code true} If job has combiner. */
    private boolean hasCombiner;

    /** Number of reducers configured for job. */
    private int numReduces;

    /** Configuration. */
    private Map<String,String> props = new HashMap<>();

    /** Job name. */
    private String jobName;

    /** User name. */
    private String user;

    /** Credentials. */
    private byte[] credentials;

    /**
     * Default constructor required by {@link Externalizable}.
     */
    public HadoopDefaultJobInfo() {
        // No-op.
    }

    /**
     * Constructor.
     *
     * @param jobName Job name.
     * @param user User name.
     * @param hasCombiner {@code true} If job has combiner.
     * @param numReduces Number of reducers configured for job.
     * @param props All other properties of the job.
     */
    public HadoopDefaultJobInfo(String jobName, String user, boolean hasCombiner, int numReduces,
        Map<String, String> props, byte[] credentials) {
        this.jobName = jobName;
        this.user = user;
        this.hasCombiner = hasCombiner;
        this.numReduces = numReduces;
        this.props = props;
        this.credentials = credentials;
    }

    /** {@inheritDoc} */
    @Nullable @Override public String property(String name) {
        return props.get(name);
    }

    /** {@inheritDoc} */
    @Override public HadoopJobEx createJob(Class<? extends HadoopJobEx> jobCls, HadoopJobId jobId, IgniteLogger log,
        @Nullable String[] libNames, HadoopHelper helper) throws IgniteCheckedException {
        assert jobCls != null;

        try {
            Constructor<? extends HadoopJobEx> constructor = jobCls.getConstructor(HadoopJobId.class,
                HadoopDefaultJobInfo.class, IgniteLogger.class, String[].class, HadoopHelper.class);

            return constructor.newInstance(jobId, this, log, libNames, helper);
        }
        catch (Throwable t) {
            if (t instanceof Error)
                throw (Error)t;
            
            throw new IgniteCheckedException(t);
        }
    }

    /** {@inheritDoc} */
    @Override public boolean hasCombiner() {
        return hasCombiner;
    }

    /** {@inheritDoc} */
    @Override public boolean hasReducer() {
        return reducers() > 0;
    }

    /** {@inheritDoc} */
    @Override public int reducers() {
        return numReduces;
    }

    /** {@inheritDoc} */
    @Override public String jobName() {
        return jobName;
    }

    /** {@inheritDoc} */
    @Override public String user() {
        return user;
    }

    /** {@inheritDoc} */
    @Override public byte[] credentials() {
        return credentials;
    }

    /** {@inheritDoc} */
    @Override public void writeExternal(ObjectOutput out) throws IOException {
        U.writeString(out, jobName);
        U.writeString(out, user);

        out.writeBoolean(hasCombiner);
        out.writeInt(numReduces);

        IgfsUtils.writeStringMap(out, props);

        U.writeByteArray(out, credentials);
    }

    /** {@inheritDoc} */
    @Override public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        jobName = U.readString(in);
        user = U.readString(in);

        hasCombiner = in.readBoolean();
        numReduces = in.readInt();

        props = IgfsUtils.readStringMap(in);

        credentials = U.readByteArray(in);
    }

    /**
     * @return Properties of the job.
     */
    public Map<String, String> properties() {
        return props;
    }
}