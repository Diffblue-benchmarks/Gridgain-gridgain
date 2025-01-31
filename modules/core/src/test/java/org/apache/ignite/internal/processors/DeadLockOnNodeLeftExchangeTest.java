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

package org.apache.ignite.internal.processors;

import org.apache.ignite.IgniteSystemProperties;
import org.apache.ignite.configuration.DataRegionConfiguration;
import org.apache.ignite.configuration.DataStorageConfiguration;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.failure.FailureHandler;
import org.apache.ignite.failure.StopNodeFailureHandler;
import org.apache.ignite.internal.IgniteInternalFuture;
import org.apache.ignite.internal.TestRecordingCommunicationSpi;
import org.apache.ignite.internal.cluster.IgniteClusterEx;
import org.apache.ignite.internal.processors.cache.ExchangeContext;
import org.apache.ignite.internal.processors.cache.distributed.dht.preloader.GridDhtPartitionsSingleMessage;
import org.apache.ignite.testframework.GridTestUtils;
import org.apache.ignite.testframework.junits.WithSystemProperty;
import org.apache.ignite.testframework.junits.common.GridCommonAbstractTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/** */
public class DeadLockOnNodeLeftExchangeTest extends GridCommonAbstractTest {
    /** {@inheritDoc} */
    @Override protected IgniteConfiguration getConfiguration(String igniteInstanceName) throws Exception {
        IgniteConfiguration cfg = super.getConfiguration(igniteInstanceName);

        cfg.setConsistentId(igniteInstanceName);

        cfg.setDataStorageConfiguration(new DataStorageConfiguration()
            .setDefaultDataRegionConfiguration(new DataRegionConfiguration()
                .setPersistenceEnabled(true)
            )
        );

        cfg.setCommunicationSpi(new TestRecordingCommunicationSpi());

        return cfg;
    }

    /** {@inheritDoc} */
    @Override protected FailureHandler getFailureHandler(String igniteInstanceName) {
        return new StopNodeFailureHandler();
    }

    /** */
    @Before
    public void before() throws Exception {
        stopAllGrids();

        cleanPersistenceDir();
    }

    /** */
    @After
    public void after() throws Exception {
        stopAllGrids();

        cleanPersistenceDir();
    }

    /** */
    @Test
    @WithSystemProperty(key = ExchangeContext.IGNITE_EXCHANGE_COMPATIBILITY_VER_1, value = "true")
    @WithSystemProperty(key = IgniteSystemProperties.IGNITE_BASELINE_AUTO_ADJUST_ENABLED, value = "false")
    public void test() throws Exception {
        startGrids(4);

        IgniteClusterEx cluster = grid(0).cluster();

        cluster.active(true);

        TestRecordingCommunicationSpi spi = TestRecordingCommunicationSpi.spi(grid(3));
        spi.blockMessages(GridDhtPartitionsSingleMessage.class, getTestIgniteInstanceName(0));

        stopGrid(1);

        spi.waitForBlocked();

        IgniteInternalFuture setBaselineTopFut = GridTestUtils.runAsync(() -> {
            cluster.setBaselineTopology(cluster.topologyVersion());
        });

        assertTrue(GridTestUtils.waitForCondition(
            () -> grid(0).context().state().clusterState().transition(),
            5_000L
        ));

        stopGrid(2);

        spi.stopBlock();

        setBaselineTopFut.get(30_000L);
    }
}
