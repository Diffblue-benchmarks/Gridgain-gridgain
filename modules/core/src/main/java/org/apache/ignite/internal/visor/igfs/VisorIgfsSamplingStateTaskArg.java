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

package org.apache.ignite.internal.visor.igfs;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import org.apache.ignite.internal.util.typedef.internal.S;
import org.apache.ignite.internal.util.typedef.internal.U;
import org.apache.ignite.internal.visor.VisorDataTransferObject;

/**
 * Argument for task returns changing of sampling state result.
 */
public class VisorIgfsSamplingStateTaskArg extends VisorDataTransferObject {
    /** */
    private static final long serialVersionUID = 0L;

    /** IGFS name. */
    private String name;

    /** {@code True} to turn on sampling, {@code false} to turn it off, {@code null} to clear sampling state. */
    private boolean enabled;

    /**
     * Default constructor.
     */
    public VisorIgfsSamplingStateTaskArg() {
        // No-op.
    }

    /**
     * @param name IGFS name.
     * @param enabled {@code True} to turn on sampling, {@code false} to turn it off, {@code null} to clear sampling state.
     */
    public VisorIgfsSamplingStateTaskArg(String name, boolean enabled) {
        this.name = name;
        this.enabled = enabled;
    }

    /**
     * @return IGFS name.
     */
    public String getName() {
        return name;
    }

    /**
     * @return {@code True} to turn on sampling, {@code false} to turn it off, {@code null} to clear sampling state.
     */
    public boolean isEnabled() {
        return enabled;
    }

    /** {@inheritDoc} */
    @Override protected void writeExternalData(ObjectOutput out) throws IOException {
        U.writeString(out, name);
        out.writeBoolean(enabled);
    }

    /** {@inheritDoc} */
    @Override protected void readExternalData(byte protoVer, ObjectInput in) throws IOException, ClassNotFoundException {
        name = U.readString(in);
        enabled = in.readBoolean();
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(VisorIgfsSamplingStateTaskArg.class, this);
    }
}
