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

package org.apache.ignite.spi.discovery;

import org.apache.ignite.IgniteCheckedException;
import org.apache.ignite.cluster.ClusterNode;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.events.EventType;
import org.apache.ignite.internal.IgniteEx;
import org.apache.ignite.internal.util.future.IgniteFinishedFutureImpl;
import org.apache.ignite.lang.IgniteFuture;
import org.apache.ignite.spi.discovery.tcp.TcpDiscoverySpi;
import org.apache.ignite.spi.discovery.tcp.ipfinder.vm.TcpDiscoveryVmIpFinder;
import org.apache.ignite.spi.discovery.tcp.messages.TcpDiscoveryAbstractMessage;
import org.apache.ignite.spi.discovery.tcp.messages.TcpDiscoveryNodeAddFinishedMessage;
import org.apache.ignite.testframework.junits.common.GridCommonAbstractTest;
import org.jetbrains.annotations.Nullable;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import org.junit.Test;

/**
 * Test client connects to two nodes cluster during time more than the
 * {@link org.apache.ignite.configuration.IgniteConfiguration#clientFailureDetectionTimeout}.
 */
public class LongClientConnectToClusterTest extends GridCommonAbstractTest {
    /** Client instance name. */
    public static final String CLIENT_INSTANCE_NAME = "client";
    /** Client metrics update count. */
    private static volatile int clientMetricsUpdateCnt;

    /** {@inheritDoc} */
    @Override protected IgniteConfiguration getConfiguration(String igniteInstanceName) throws Exception {
        TcpDiscoverySpi discoSpi = getTestIgniteInstanceName(0).equals(igniteInstanceName)
            ? new DelayedTcpDiscoverySpi()
            : getTestIgniteInstanceName(1).equals(igniteInstanceName)
            ? new UpdateMetricsInterceptorTcpDiscoverySpi()
            : new TcpDiscoverySpi();

        return super.getConfiguration(igniteInstanceName)
            .setClientMode(igniteInstanceName.startsWith(CLIENT_INSTANCE_NAME))
            .setClientFailureDetectionTimeout(1_000)
            .setMetricsUpdateFrequency(500)
            .setDiscoverySpi(discoSpi
                .setReconnectCount(1)
                .setLocalAddress("127.0.0.1")
                .setIpFinder(new TcpDiscoveryVmIpFinder()
                    .setAddresses(Collections.singletonList(igniteInstanceName.startsWith(CLIENT_INSTANCE_NAME)
                        ? "127.0.0.1:47501"
                        : "127.0.0.1:47500..47502"))));
    }

    /** {@inheritDoc} */
    @Override protected void beforeTest() throws Exception {
        startGrids(2);
    }

    /** {@inheritDoc} */
    @Override protected void afterTest() throws Exception {
        stopAllGrids();
    }

    /**
     * Test method.
     *
     * @throws Exception If failed.
     */
    @Test
    public void testClientConnectToCluster() throws Exception {
        clientMetricsUpdateCnt = 0;

        IgniteEx client = startGrid(CLIENT_INSTANCE_NAME);

        assertTrue(clientMetricsUpdateCnt > 0);

        assertTrue(client.localNode().isClient());

        assertEquals(client.cluster().nodes().size(), 3);
    }

    /** Discovery SPI which intercept TcpDiscoveryClientMetricsUpdateMessage. */
    private static class UpdateMetricsInterceptorTcpDiscoverySpi extends TcpDiscoverySpi {
        /** */
        private class DiscoverySpiListenerWrapper implements DiscoverySpiListener {
            /** */
            private DiscoverySpiListener delegate;

            /**
             * @param delegate Delegate.
             */
            private DiscoverySpiListenerWrapper(DiscoverySpiListener delegate) {
                this.delegate = delegate;
            }

            /** {@inheritDoc} */
            @Override public IgniteFuture<?> onDiscovery(
                int type,
                long topVer,
                ClusterNode node,
                Collection<ClusterNode> topSnapshot,
                @Nullable Map<Long, Collection<ClusterNode>> topHist,
                @Nullable DiscoverySpiCustomMessage spiCustomMsg
            ) {
                if (EventType.EVT_NODE_METRICS_UPDATED == type) {
                    log.info("Metrics update message catched from node " + node);

                    assertFalse(locNode.isClient());

                    if (node.isClient())
                        clientMetricsUpdateCnt++;
                }

                if (delegate != null)
                    return delegate.onDiscovery(type, topVer, node, topSnapshot, topHist, spiCustomMsg);

                return new IgniteFinishedFutureImpl<>();
            }

            /** {@inheritDoc} */
            @Override public void onLocalNodeInitialized(ClusterNode locNode) {
                if (delegate != null)
                    delegate.onLocalNodeInitialized(locNode);
            }
        }

        /** {@inheritDoc} */
        @Override public void setListener(@Nullable DiscoverySpiListener lsnr) {
            super.setListener(new DiscoverySpiListenerWrapper(lsnr));
        }
    }

    /** Discovery SPI delayed TcpDiscoveryNodeAddFinishedMessage. */
    private static class DelayedTcpDiscoverySpi extends TcpDiscoverySpi {
        /** Delay message period millis. */
        public static final int DELAY_MSG_PERIOD_MILLIS = 2_000;

        /** {@inheritDoc} */
        @Override protected void writeToSocket(ClusterNode node, Socket sock, OutputStream out,
            TcpDiscoveryAbstractMessage msg, long timeout) throws IOException, IgniteCheckedException {
            if (msg instanceof TcpDiscoveryNodeAddFinishedMessage && msg.topologyVersion() == 3) {
                log.info("Catched discovery message: " + msg);

                try {
                    Thread.sleep(DELAY_MSG_PERIOD_MILLIS);
                }
                catch (InterruptedException e) {
                    log.error("Interrupt on DelayedTcpDiscoverySpi.", e);

                    Thread.currentThread().interrupt();
                }
            }

            super.writeToSocket(node, sock, out, msg, timeout);
        }
    }
}
