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

package org.apache.ignite.spi.discovery.tcp;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCheckedException;
import org.apache.ignite.cluster.ClusterNode;
import org.apache.ignite.configuration.CacheConfiguration;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.internal.DiscoverySpiTestListener;
import org.apache.ignite.internal.IgniteEx;
import org.apache.ignite.internal.IgniteInternalFuture;
import org.apache.ignite.internal.managers.discovery.CustomMessageWrapper;
import org.apache.ignite.internal.managers.discovery.DiscoveryCustomMessage;
import org.apache.ignite.internal.util.typedef.internal.U;
import org.apache.ignite.lang.IgniteBiClosure;
import org.apache.ignite.spi.discovery.DiscoverySpiCustomMessage;
import org.apache.ignite.spi.discovery.tcp.ipfinder.vm.TcpDiscoveryVmIpFinder;
import org.apache.ignite.spi.discovery.tcp.messages.TcpDiscoveryAbstractMessage;
import org.apache.ignite.spi.discovery.tcp.messages.TcpDiscoveryCustomEventMessage;
import org.apache.ignite.testframework.junits.common.GridCommonAbstractTest;
import org.junit.Test;

/**
 * Test scenario:
 *
 * 1. Create topology in specific order: srv1 srv2 client srv3 srv4
 * 2. Delay client reconnect.
 * 3. Trigger topology change by restarting srv2 (will trigger reconnect to next node), srv3, srv4
 * 4. Resume reconnect to node with empty EnsuredMessageHistory and wait for completion.
 * 5. Add new node to topology.
 *
 * Pass condition: new node successfully joins topology.
 */
public class TcpDiscoveryReconnectUnstableTopologyTest extends GridCommonAbstractTest {
    /** */
    private static final TcpDiscoveryVmIpFinder ipFinder = new TcpDiscoveryVmIpFinder(true);

    /** {@inheritDoc} */
    @Override protected IgniteConfiguration getConfiguration(String igniteInstanceName) throws Exception {
        IgniteConfiguration cfg = super.getConfiguration(igniteInstanceName);
        cfg.setAutoActivationEnabled(false);

        BlockTcpDiscoverySpi spi = new BlockTcpDiscoverySpi();

        // Guarantees client join to srv2.
        Field rndAddrsField = U.findField(BlockTcpDiscoverySpi.class, "skipAddrsRandomization");

        assertNotNull(rndAddrsField);

        rndAddrsField.set(spi, true);

        cfg.setDiscoverySpi(spi.setIpFinder(ipFinder));

        cfg.setClientMode(igniteInstanceName.startsWith("client"));

        cfg.setCacheConfiguration(new CacheConfiguration(DEFAULT_CACHE_NAME));

        return cfg;
    }

    /**
     * @throws Exception If failed.
     */
    @Test
    public void testReconnectUnstableTopology() throws Exception {
        try {
            List<IgniteEx> nodes = new ArrayList<>();

            nodes.add(startGrid(0));

            nodes.add(startGrid(1));

            nodes.add(startGrid("client"));

            nodes.add(startGrid(2));

            nodes.add(startGrid(3));

            for (int i = 0; i < nodes.size(); i++) {
                IgniteEx ex = nodes.get(i);

                assertEquals(i + 1, ex.localNode().order());
            }

            DiscoverySpiTestListener lsnr = new DiscoverySpiTestListener();

            spi(grid("client")).setInternalListener(lsnr);

            lsnr.startBlockReconnect();

            CountDownLatch restartLatch = new CountDownLatch(1);

            IgniteInternalFuture<?> fut = multithreadedAsync(() -> {
                stopGrid(1);
                stopGrid(2);
                stopGrid(3);
                try {
                    startGrid(1);
                    startGrid(2);
                    startGrid(3);
                }
                catch (Exception e) {
                    fail();
                }

                restartLatch.countDown();
            }, 1, "restarter");

            U.awaitQuiet(restartLatch);

            lsnr.stopBlockRestart();

            fut.get();

            doSleep(1500); // Wait for reconnect.

            startGrid(4);
        }
        finally {
            stopAllGrids();
        }
    }

    /**
     * @param ig Ignite.
     */
    private TcpDiscoverySpi spi(Ignite ig) {
        return (TcpDiscoverySpi)ig.configuration().getDiscoverySpi();
    }

    /**
     * Discovery SPI with blocking support.
     */
    protected class BlockTcpDiscoverySpi extends TcpDiscoverySpi {
        /** Closure. */
        private volatile IgniteBiClosure<ClusterNode, DiscoveryCustomMessage, Void> clo;

        /**
         * @param clo Closure.
         */
        public void setClosure(IgniteBiClosure<ClusterNode, DiscoveryCustomMessage, Void> clo) {
            this.clo = clo;
        }

        /**
         * @param addr Address.
         * @param msg Message.
         */
        private synchronized void apply(ClusterNode addr, TcpDiscoveryAbstractMessage msg) {
            if (!(msg instanceof TcpDiscoveryCustomEventMessage))
                return;

            TcpDiscoveryCustomEventMessage cm = (TcpDiscoveryCustomEventMessage)msg;

            DiscoveryCustomMessage delegate;

            try {
                DiscoverySpiCustomMessage custMsg = cm.message(marshaller(), U.resolveClassLoader(ignite().configuration()));

                assertNotNull(custMsg);

                delegate = ((CustomMessageWrapper)custMsg).delegate();

            }
            catch (Throwable throwable) {
                throw new RuntimeException(throwable);
            }

            if (clo != null)
                clo.apply(addr, delegate);
        }

        /** {@inheritDoc} */
        @Override protected void writeToSocket(
            Socket sock,
            TcpDiscoveryAbstractMessage msg,
            byte[] data,
            long timeout
        ) throws IOException {
            if (spiCtx != null)
                apply(spiCtx.localNode(), msg);

            super.writeToSocket(sock, msg, data, timeout);
        }

        /** {@inheritDoc} */
        @Override protected void writeToSocket(Socket sock,
            OutputStream out,
            TcpDiscoveryAbstractMessage msg,
            long timeout) throws IOException, IgniteCheckedException {
            if (spiCtx != null)
                apply(spiCtx.localNode(), msg);

            super.writeToSocket(sock, out, msg, timeout);
        }
    }
}
