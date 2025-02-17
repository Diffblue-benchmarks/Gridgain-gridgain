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

namespace Apache.Ignite.Core.Datastream
{
    using System.Collections.Generic;
    using System.Diagnostics.CodeAnalysis;
    using Apache.Ignite.Core.Binary;
    using Apache.Ignite.Core.Cache;
    using Apache.Ignite.Core.Impl.Binary;
    using Apache.Ignite.Core.Impl.Common;
    using Apache.Ignite.Core.Impl.Datastream;

    /// <summary>
    /// Convenience adapter to transform update existing values in streaming cache 
    /// based on the previously cached value.
    /// </summary>
    /// <typeparam name="TK">Key type.</typeparam>
    /// <typeparam name="TV">Value type.</typeparam>
    /// <typeparam name="TArg">The type of the processor argument.</typeparam>
    /// <typeparam name="TRes">The type of the processor result.</typeparam>
    public sealed class StreamTransformer<TK, TV, TArg, TRes> : IStreamReceiver<TK, TV>, 
        IBinaryWriteAware
    {
        /** Entry processor. */
        private readonly ICacheEntryProcessor<TK, TV, TArg, TRes> _proc;

        /// <summary>
        /// Initializes a new instance of the <see cref="StreamTransformer{K, V, A, R}"/> class.
        /// </summary>
        /// <param name="proc">Entry processor.</param>
        public StreamTransformer(ICacheEntryProcessor<TK, TV, TArg, TRes> proc)
        {
            IgniteArgumentCheck.NotNull(proc, "proc");

            _proc = proc;
        }

        /// <summary>
        /// Updates cache with batch of entries.
        /// </summary>
        /// <param name="cache">Cache.</param>
        /// <param name="entries">Entries.</param>
        [SuppressMessage("Microsoft.Design", "CA1062:Validate arguments of public methods")]
        public void Receive(ICache<TK, TV> cache, ICollection<ICacheEntry<TK, TV>> entries)
        {
            var keys = new List<TK>(entries.Count);

            foreach (var entry in entries)
                keys.Add(entry.Key);

            cache.InvokeAll(keys, _proc, default(TArg));
        }

        /** <inheritdoc /> */
        void IBinaryWriteAware.WriteBinary(IBinaryWriter writer)
        {
            var w = (BinaryWriter)writer;

            w.WriteByte(StreamReceiverHolder.RcvTransformer);

            w.WriteObject(_proc);
        }
    }
}