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

package org.apache.ignite.tensorflow.cluster.spec;

import java.io.Serializable;
import java.util.Collection;
import java.util.UUID;
import org.apache.ignite.Ignite;

/**
 * TensorFlow server address specification.
 */
public class TensorFlowServerAddressSpec implements Serializable {
    /** */
    private static final long serialVersionUID = 7883701602323727681L;

    /** Node identifier. */
    private final UUID nodeId;

    /** Port. */
    private final int port;

    /**
     * Constructs a new instance of TensorFlow server address specification.
     *
     * @param nodeId Node identifier.
     * @param port Port.
     */
    TensorFlowServerAddressSpec(UUID nodeId, int port) {
        assert nodeId != null : "Node identifier should not be null";
        assert port >= 0 && port <= 0xFFFF : "Port should be between 0 and 65535";

        this.nodeId = nodeId;
        this.port = port;
    }

    /**
     * Formats Server Address specification so that TensorFlow accepts it.
     *
     * @param ignite Ignite instance.
     * @return Formatted server address specification.
     */
    public String format(Ignite ignite) {
        Collection<String> names = ignite.cluster().forNodeId(nodeId).hostNames();

        return names.iterator().next() + ":" + port;
    }

    /** */
    public UUID getNodeId() {
        return nodeId;
    }

    /** */
    public int getPort() {
        return port;
    }
}
