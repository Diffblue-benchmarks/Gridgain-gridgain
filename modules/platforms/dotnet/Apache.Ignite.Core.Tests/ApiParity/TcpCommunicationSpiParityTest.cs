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

namespace Apache.Ignite.Core.Tests.ApiParity
{
    using System.Collections.Generic;
    using Apache.Ignite.Core.Cache.Configuration;
    using Apache.Ignite.Core.Communication.Tcp;
    using NUnit.Framework;

    /// <summary>
    /// Tests that .NET <see cref="CacheConfiguration"/> has all properties from Java configuration APIs.
    /// </summary>
    public class TcpCommunicationSpiParityTest
    {
        /** Known property name mappings. */
        private static readonly Dictionary<string, string> KnownMappings = new Dictionary<string, string>()
        {
            {"SocketReceiveBuffer", "SocketReceiveBufferSize"},
            {"SocketSendBuffer", "SocketSendBufferSize"}
        };

        /** Properties that are not needed on .NET side. */
        private static readonly string[] UnneededProperties =
        {
            // Java-specific.
            "AddressResolver",
            "Listener",
            "run",
            "ReceivedMessagesByType",
            "ReceivedMessagesByNode",
            "SentMessagesByType",
            "SentMessagesByNode",
            "SentMessagesCount",
            "SentBytesCount",
            "ReceivedMessagesCount",
            "ReceivedBytesCount",
            "OutboundMessagesQueueSize",
            "resetMetrics",
            "dumpStats",
            "boundPort",
            "SpiContext",
            "simulateNodeFailure",
            "cancel",
            "order",
            "onTimeout",
            "endTime",
            "id",
            "connectionIndex",
            "NodeFilter"
        };

        /** Properties that are missing on .NET side. */
        private static readonly string[] MissingProperties = {};

        /// <summary>
        /// Tests the cache configuration parity.
        /// </summary>
        [Test]
        public void TestTcpCommunicationSpi()
        {
            ParityTest.CheckConfigurationParity(
                @"modules\core\src\main\java\org\apache\ignite\spi\communication\tcp\TcpCommunicationSpi.java", 
                typeof(TcpCommunicationSpi),
                UnneededProperties,
                MissingProperties,
                KnownMappings);
        }
    }
}
