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

package org.apache.ignite.internal.processors.rest.protocols.tcp.redis;

import java.util.EnumMap;
import java.util.Map;
import org.apache.ignite.IgniteCheckedException;
import org.apache.ignite.IgniteLogger;
import org.apache.ignite.internal.GridKernalContext;
import org.apache.ignite.internal.IgniteInternalFuture;
import org.apache.ignite.internal.processors.rest.GridRestProtocolHandler;
import org.apache.ignite.internal.processors.rest.handlers.redis.GridRedisCommandHandler;
import org.apache.ignite.internal.processors.rest.handlers.redis.GridRedisConnectionCommandHandler;
import org.apache.ignite.internal.processors.rest.handlers.redis.key.GridRedisDelCommandHandler;
import org.apache.ignite.internal.processors.rest.handlers.redis.key.GridRedisExistsCommandHandler;
import org.apache.ignite.internal.processors.rest.handlers.redis.key.GridRedisExpireCommandHandler;
import org.apache.ignite.internal.processors.rest.handlers.redis.server.GridRedisDbSizeCommandHandler;
import org.apache.ignite.internal.processors.rest.handlers.redis.server.GridRedisFlushCommandHandler;
import org.apache.ignite.internal.processors.rest.handlers.redis.string.GridRedisAppendCommandHandler;
import org.apache.ignite.internal.processors.rest.handlers.redis.string.GridRedisGetCommandHandler;
import org.apache.ignite.internal.processors.rest.handlers.redis.string.GridRedisGetRangeCommandHandler;
import org.apache.ignite.internal.processors.rest.handlers.redis.string.GridRedisGetSetCommandHandler;
import org.apache.ignite.internal.processors.rest.handlers.redis.string.GridRedisIncrDecrCommandHandler;
import org.apache.ignite.internal.processors.rest.handlers.redis.string.GridRedisMGetCommandHandler;
import org.apache.ignite.internal.processors.rest.handlers.redis.string.GridRedisMSetCommandHandler;
import org.apache.ignite.internal.processors.rest.handlers.redis.string.GridRedisSetCommandHandler;
import org.apache.ignite.internal.processors.rest.handlers.redis.string.GridRedisSetRangeCommandHandler;
import org.apache.ignite.internal.processors.rest.handlers.redis.string.GridRedisStrlenCommandHandler;
import org.apache.ignite.internal.util.nio.GridNioFuture;
import org.apache.ignite.internal.util.nio.GridNioServerListenerAdapter;
import org.apache.ignite.internal.util.nio.GridNioSession;
import org.apache.ignite.internal.util.nio.GridNioSessionMetaKey;
import org.apache.ignite.internal.util.typedef.CIX1;
import org.apache.ignite.internal.util.typedef.internal.U;
import org.jetbrains.annotations.Nullable;

/**
 * Listener for Redis protocol requests.
 */
public class GridRedisNioListener extends GridNioServerListenerAdapter<GridRedisMessage> {
    /** Logger. */
    private final IgniteLogger log;

    /** Redis-specific handlers. */
    protected final Map<GridRedisCommand, GridRedisCommandHandler> handlers = new EnumMap<>(GridRedisCommand.class);

    /** Connection-related metadata key. Used for cache name only. */
    public static final int CONN_CTX_META_KEY = GridNioSessionMetaKey.nextUniqueKey();

    /**
     * @param log Logger.
     * @param hnd REST protocol handler.
     * @param ctx Context.
     */
    public GridRedisNioListener(IgniteLogger log, GridRestProtocolHandler hnd, GridKernalContext ctx) {
        this.log = log;

        // connection commands.
        addCommandHandler(new GridRedisConnectionCommandHandler(log, hnd, ctx));

        // string commands.
        addCommandHandler(new GridRedisGetCommandHandler(log, hnd, ctx));
        addCommandHandler(new GridRedisSetCommandHandler(log, hnd, ctx));
        addCommandHandler(new GridRedisMSetCommandHandler(log, hnd));
        addCommandHandler(new GridRedisMGetCommandHandler(log, hnd));
        addCommandHandler(new GridRedisIncrDecrCommandHandler(log, hnd));
        addCommandHandler(new GridRedisAppendCommandHandler(log, hnd));
        addCommandHandler(new GridRedisGetSetCommandHandler(log, hnd));
        addCommandHandler(new GridRedisStrlenCommandHandler(log, hnd));
        addCommandHandler(new GridRedisSetRangeCommandHandler(log, hnd));
        addCommandHandler(new GridRedisGetRangeCommandHandler(log, hnd));

        // key commands.
        addCommandHandler(new GridRedisDelCommandHandler(log, hnd));
        addCommandHandler(new GridRedisExistsCommandHandler(log, hnd));
        addCommandHandler(new GridRedisExpireCommandHandler(log, hnd));

        // server commands.
        addCommandHandler(new GridRedisDbSizeCommandHandler(log, hnd));
        addCommandHandler(new GridRedisFlushCommandHandler(log, hnd, ctx));
    }

    /**
     * Adds Redis-specific command handlers.
     * <p>
     * Generic commands are treated by REST.
     *
     * @param hnd Redis-specific command handler.
     */
    private void addCommandHandler(GridRedisCommandHandler hnd) {
        assert !handlers.containsValue(hnd);

        if (log.isDebugEnabled())
            log.debug("Added Redis command handler: " + hnd);

        for (GridRedisCommand cmd : hnd.supportedCommands()) {
            assert !handlers.containsKey(cmd) : cmd;

            handlers.put(cmd, hnd);
        }
    }

    /** {@inheritDoc} */
    @Override public void onConnected(GridNioSession ses) {
        // No-op, never called.
        assert false;
    }

    /** {@inheritDoc} */
    @Override public void onDisconnected(GridNioSession ses, @Nullable Exception e) {
        // No-op, never called.
        assert false;
    }

    /** {@inheritDoc} */
    @Override public void onMessage(final GridNioSession ses, final GridRedisMessage msg) {
        if (handlers.get(msg.command()) == null) {
            U.warn(log, "Cannot find the corresponding command (session will be closed) [ses=" + ses +
                ", command=" + msg.command().name() + ']');

            ses.close();

            return;
        }
        else {
            String cacheName = ses.meta(CONN_CTX_META_KEY);

            if (cacheName != null)
                msg.cacheName(cacheName);

            IgniteInternalFuture<GridRedisMessage> f = handlers.get(msg.command()).handleAsync(ses, msg);

            f.listen(new CIX1<IgniteInternalFuture<GridRedisMessage>>() {
                @Override public void applyx(IgniteInternalFuture<GridRedisMessage> f) throws IgniteCheckedException {
                    GridRedisMessage res = f.get();

                    sendResponse(ses, res);
                }
            });
        }
    }

    /**
     * Sends a response to be encoded and sent to the Redis client.
     *
     * @param ses NIO session.
     * @param res Response.
     * @return NIO send future.
     */
    private GridNioFuture<?> sendResponse(GridNioSession ses, GridRedisMessage res) {
        return ses.send(res);
    }
}
