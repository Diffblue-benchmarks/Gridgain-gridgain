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

package org.apache.ignite.internal;

import org.apache.ignite.configuration.ExecutorConfiguration;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.internal.util.IgniteUtils;
import org.apache.ignite.testframework.junits.common.GridCommonAbstractTest;

import javax.management.ObjectName;
import org.junit.Test;

/**
 * Tests for the standard JMX beans registered by the kernal.
 */
public class GridMBeansTest extends GridCommonAbstractTest {
    /** Executor name for setExecutorConfiguration */
    private static final String CUSTOM_EXECUTOR_0 = "Custom executor 0";

    /** Executor name for setExecutorConfiguration */
    private static final String CUSTOM_EXECUTOR_1 = "Custom executor 1";

    /** Create test and auto-start the grid */
    public GridMBeansTest() {
        super(true);
    }

    /**
     * {@inheritDoc}
     *
     * This implementation registers adds custom executors to the configuration.
     */
    @Override protected IgniteConfiguration getConfiguration(String igniteInstanceName) throws Exception {
        IgniteConfiguration cfg = super.getConfiguration(igniteInstanceName);

        cfg.setExecutorConfiguration(new ExecutorConfiguration(CUSTOM_EXECUTOR_0),
            new ExecutorConfiguration(CUSTOM_EXECUTOR_1));

        return cfg;
    }

    /** Check that kernal bean is available */
    @Test
    public void testKernalBeans() throws Exception {
        checkBean("Kernal", "IgniteKernal", "InstanceName", grid().name());
        checkBean("Kernal", "ClusterMetricsMXBeanImpl", "TotalServerNodes", 1);
        checkBean("Kernal", "ClusterMetricsMXBeanImpl", "TotalServerNodes", 1);
    }

    /** Check that kernal bean is available */
    @Test
    public void testExecutorBeans() throws Exception {
        // standard executors
        checkBean("Thread Pools", "GridExecutionExecutor", "Terminated", false);
        checkBean("Thread Pools", "GridSystemExecutor", "Terminated", false);
        checkBean("Thread Pools", "GridManagementExecutor", "Terminated", false);
        checkBean("Thread Pools", "GridClassLoadingExecutor", "Terminated", false);
        checkBean("Thread Pools", "GridQueryExecutor", "Terminated", false);
        checkBean("Thread Pools", "GridSchemaExecutor", "Terminated", false);
        checkBean("Thread Pools", "StripedExecutor", "Terminated", false);

        // custom executors
        checkBean("Thread Pools", CUSTOM_EXECUTOR_0, "Terminated", false);
        checkBean("Thread Pools", CUSTOM_EXECUTOR_1, "Terminated", false);
    }

    /** Checks that a bean with the specified group and name is available and has the expected attribute */
    private void checkBean(String grp, String name, String attributeName, Object expAttributeVal) throws Exception {
        ObjectName mBeanName = IgniteUtils.makeMBeanName(grid().name(), grp, name);
        Object attributeVal = grid().configuration().getMBeanServer().getAttribute(mBeanName, attributeName);

        assertEquals(expAttributeVal, attributeVal);
    }
}
