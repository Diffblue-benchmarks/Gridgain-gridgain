/*
 *                   GridGain Community Edition Licensing
 *                   Copyright 2019 GridGain Systems, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License") modified with Commons Clause
 * Restriction; you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * Commons Clause Restriction
 *
 * The Software is provided to you by the Licensor under the License, as defined below, subject to
 * the following condition.
 *
 * Without limiting other conditions in the License, the grant of rights under the License will not
 * include, and the License does not grant to you, the right to Sell the Software.
 * For purposes of the foregoing, “Sell” means practicing any or all of the rights granted to you
 * under the License to provide to third parties, for a fee or other consideration (including without
 * limitation fees for hosting or consulting/ support services related to the Software), a product or
 * service whose value derives, entirely or substantially, from the functionality of the Software.
 * Any license notice or attribution required by the License must also include this Commons Clause
 * License Condition notice.
 *
 * For purposes of the clause above, the “Licensor” is Copyright 2019 GridGain Systems, Inc.,
 * the “License” is the Apache License, Version 2.0, and the Software is the GridGain Community
 * Edition software provided with this notice.
 */
package org.apache.ignite.internal.processors.cache.binary;

import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.ignite.IgniteCheckedException;
import org.apache.ignite.IgniteLogger;
import org.apache.ignite.binary.BinaryObjectException;
import org.apache.ignite.cluster.ClusterNode;
import org.apache.ignite.internal.GridKernalContext;
import org.apache.ignite.internal.GridTopic;
import org.apache.ignite.internal.managers.communication.GridIoManager;
import org.apache.ignite.internal.managers.communication.GridIoPolicy;
import org.apache.ignite.internal.managers.discovery.GridDiscoveryManager;
import org.apache.ignite.internal.util.future.GridFutureAdapter;
import org.apache.ignite.internal.util.typedef.internal.U;
import org.jetbrains.annotations.Nullable;

/**
 * Future is responsible for requesting up-to-date metadata from any server node in cluster
 * and for blocking thread on client node until response arrives.
 *
 * It can cope with situation if node currently requested for the metadata leaves cluster;
 * in that case future simply re-requests metadata from the next node available in topology.
 */
final class ClientMetadataRequestFuture extends GridFutureAdapter<MetadataUpdateResult> {
    /** */
    private static final AtomicReference<IgniteLogger> logRef = new AtomicReference<>();

    /** */
    private static IgniteLogger log;

    /** */
    private final GridIoManager ioMgr;

    /** */
    private final GridDiscoveryManager discoMgr;

    /** */
    private final int typeId;

    /** */
    private final Map<Integer, ClientMetadataRequestFuture> syncMap;

    /** */
    private final Queue<ClusterNode> aliveSrvNodes;

    /** */
    private ClusterNode pendingNode;

    /**
     * @param ctx Context.
     * @param syncMap Map to store futures for ongoing requests.
     */
    ClientMetadataRequestFuture(
            GridKernalContext ctx,
            int typeId,
            Map<Integer, ClientMetadataRequestFuture> syncMap
    ) {
        ioMgr = ctx.io();
        discoMgr = ctx.discovery();
        aliveSrvNodes = new LinkedList<>(discoMgr.aliveServerNodes());

        this.typeId = typeId;
        this.syncMap = syncMap;

        if (log == null)
            log = U.logger(ctx, logRef, ClientMetadataRequestFuture.class);
    }

    /** */
    void requestMetadata() {
        boolean noSrvsInCluster;

        synchronized (this) {
            while (!aliveSrvNodes.isEmpty()) {
                ClusterNode srvNode = aliveSrvNodes.poll();

                try {
                    if (log.isDebugEnabled())
                        log.debug("Requesting metadata for typeId " + typeId +
                            " from node " + srvNode.id()
                        );

                    ioMgr.sendToGridTopic(srvNode,
                            GridTopic.TOPIC_METADATA_REQ,
                            new MetadataRequestMessage(typeId),
                            GridIoPolicy.SYSTEM_POOL);

                    if (discoMgr.node(srvNode.id()) == null)
                        continue;

                    pendingNode = srvNode;

                    break;
                }
                catch (IgniteCheckedException ignored) {
                    U.warn(log,
                            "Failed to request marshaller mapping from remote node (proceeding with the next one): "
                                    + srvNode);
                }
            }

            noSrvsInCluster = pendingNode == null;
        }

        if (noSrvsInCluster)
            onDone(MetadataUpdateResult.createFailureResult(
                    new BinaryObjectException(
                            "All server nodes have left grid, cannot request metadata [typeId: "
                                    + typeId + "]")));
    }

    /**
     * If left node is the one latest metadata request was sent to,
     * request is sent again to the next node in topology.
     *
     * @param leftNodeId ID of left node.
     */
    void onNodeLeft(UUID leftNodeId) {
        boolean reqAgain = false;

        synchronized (this) {
            if (pendingNode != null && pendingNode.id().equals(leftNodeId)) {
                aliveSrvNodes.remove(pendingNode);

                pendingNode = null;

                reqAgain = true;
            }
        }

        if (reqAgain)
            requestMetadata();
    }

    /** {@inheritDoc} */
    @Override public boolean onDone(@Nullable MetadataUpdateResult res, @Nullable Throwable err) {
        assert res != null;

        boolean done = super.onDone(res, err);

        if (done)
            syncMap.remove(typeId);

        return done;
    }
}
