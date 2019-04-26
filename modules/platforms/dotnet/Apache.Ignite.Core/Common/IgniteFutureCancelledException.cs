﻿/*
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

namespace Apache.Ignite.Core.Common
{
    using System;
    using System.Runtime.Serialization;

    /// <summary>
    /// Indicates future cancellation within Ignite.
    /// </summary>
    [Serializable]
    public class IgniteFutureCancelledException : IgniteException
    {
        /// <summary>
        /// Initializes a new instance of the <see cref="IgniteFutureCancelledException"/> class.
        /// </summary>
        public IgniteFutureCancelledException()
        {
            // No-op.
        }

        /// <summary>
        /// Initializes a new instance of the <see cref="IgniteFutureCancelledException"/> class.
        /// </summary>
        /// <param name="message">The message that describes the error.</param>
        public IgniteFutureCancelledException(string message) : base(message)
        {
            // No-op.
        }

        /// <summary>
        /// Initializes a new instance of the <see cref="IgniteFutureCancelledException"/> class.
        /// </summary>
        /// <param name="message">The message.</param>
        /// <param name="cause">The cause.</param>
        public IgniteFutureCancelledException(string message, Exception cause) : base(message, cause)
        {
            // No-op.
        }

        /// <summary>
        /// Initializes a new instance of the <see cref="IgniteFutureCancelledException"/> class.
        /// </summary>
        /// <param name="info">Serialization information.</param>
        /// <param name="ctx">Streaming context.</param>
        protected IgniteFutureCancelledException(SerializationInfo info, StreamingContext ctx) : base(info, ctx)
        {
            // No-op.
        }
    }
}
