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

package org.apache.ignite.internal.client.thin;

import java.util.function.Consumer;
import java.util.function.Function;
import org.apache.ignite.internal.binary.streams.BinaryInputStream;
import org.apache.ignite.internal.binary.streams.BinaryOutputStream;
import org.apache.ignite.client.ClientConnectionException;
import org.apache.ignite.client.ClientAuthorizationException;

/**
 * Processing thin client requests and responses.
 */
interface ClientChannel extends AutoCloseable {
    /**
     * @param op Operation.
     * @param payloadWriter Payload writer to stream or {@code null} if request has no payload.
     * @return Request ID.
     */
    public long send(ClientOperation op, Consumer<BinaryOutputStream> payloadWriter) throws ClientConnectionException;

    /**
     * @param op Operation.
     * @param reqId ID of the request to receive the response for.
     * @param payloadReader Payload reader from stream.
     * @return Received operation payload or {@code null} if response has no payload.
     */
    public <T> T receive(ClientOperation op, long reqId, Function<BinaryInputStream, T> payloadReader)
        throws ClientConnectionException, ClientAuthorizationException;

    /**
     * @return Server version.
     */
    public ProtocolVersion serverVersion();
}
