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

package org.apache.ignite.internal.processors.platform.client.cache;

import org.apache.ignite.IgniteCache;
import org.apache.ignite.binary.BinaryRawReader;
import org.apache.ignite.internal.processors.cache.DynamicCacheDescriptor;
import org.apache.ignite.internal.processors.platform.client.ClientConnectionContext;
import org.apache.ignite.internal.processors.platform.client.ClientRequest;
import org.apache.ignite.internal.processors.platform.client.ClientStatus;
import org.apache.ignite.internal.processors.platform.client.IgniteClientException;

/**
 * Cache get request.
 */
class ClientCacheRequest extends ClientRequest {
    /** Flag: keep binary. */
    private static final byte FLAG_KEEP_BINARY = 1;

    /** Cache ID. */
    private final int cacheId;

    /** Flags. */
    private final byte flags;

    /**
     * Constructor.
     *
     * @param reader Reader.
     */
    ClientCacheRequest(BinaryRawReader reader) {
        super(reader);

        cacheId = reader.readInt();

        flags = reader.readByte();
    }

    /**
     * Gets the cache for current cache id, with binary mode enabled.
     *
     * @param ctx Kernal context.
     * @return Cache.
     */
    protected IgniteCache cache(ClientConnectionContext ctx) {
        return rawCache(ctx).withKeepBinary();
    }

    /**
     *  Gets a value indicating whether keepBinary flag is set in this request.
     *
     * @return keepBinary flag value.
     */
    protected boolean isKeepBinary() {
        return (flags & FLAG_KEEP_BINARY) == FLAG_KEEP_BINARY;
    }

    /**
     * Gets the cache for current cache id, ignoring any flags.
     *
     * @param ctx Kernal context.
     * @return Cache.
     */
    protected IgniteCache rawCache(ClientConnectionContext ctx) {
        DynamicCacheDescriptor cacheDesc = cacheDescriptor(ctx);

        String cacheName = cacheDesc.cacheName();

        return ctx.kernalContext().grid().cache(cacheName);
    }

    /**
     * Gets the cache descriptor.
     *
     * @param ctx Context.
     * @return Cache descriptor.
     */
    protected DynamicCacheDescriptor cacheDescriptor(ClientConnectionContext ctx) {
        return cacheDescriptor(ctx, cacheId);
    }

    /**
     * Gets the cache descriptor.
     *
     * @param ctx Context.
     * @param cacheId Cache id.
     * @return Cache descriptor.
     */
    public static DynamicCacheDescriptor cacheDescriptor(ClientConnectionContext ctx, int cacheId) {
        DynamicCacheDescriptor desc = ctx.kernalContext().cache().cacheDescriptor(cacheId);

        if (desc == null)
            throw new IgniteClientException(ClientStatus.CACHE_DOES_NOT_EXIST, "Cache does not exist [cacheId= " +
                    cacheId + "]", null);

        return desc;
    }

    /**
     * Gets the cache id.
     *
     * @return Cache id.
     */
    protected int cacheId() {
        return cacheId;
    }
}
