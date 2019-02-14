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

package org.apache.ignite.internal;

import java.io.File;
import org.apache.ignite.configuration.DataRegionConfiguration;
import org.apache.ignite.configuration.DataStorageConfiguration;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.internal.util.GridDebug;
import org.apache.ignite.internal.util.typedef.internal.U;
import org.apache.ignite.testframework.junits.common.GridCommonAbstractTest;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests leaks on node restart with enabled persistence.
 */
@RunWith(JUnit4.class)
public class MemoryLeaksOnRestartNodeTest extends GridCommonAbstractTest {
    /** Heap dump file name. */
    private static final String HEAP_DUMP_FILE_NAME = "test.hprof";

    /** Restarts count. */
    private static final int RESTARTS = 10;

    /** Nodes count. */
    private static final int NODES = 3;

    /** Allow 5Mb leaks on node restart. */
    private static final int ALLOW_LEAK_ON_RESTART_IN_MB = 1;

    /** {@inheritDoc} */
    @Override protected IgniteConfiguration getConfiguration(String igniteInstanceName) throws Exception {
        IgniteConfiguration cfg = super.getConfiguration(igniteInstanceName);

        cfg.setDataStorageConfiguration(new DataStorageConfiguration()
            .setDefaultDataRegionConfiguration(
                new DataRegionConfiguration().setName("mem0").setPersistenceEnabled(false))
            .setDataRegionConfigurations(
                new DataRegionConfiguration().setName("disk").setPersistenceEnabled(true),
                new DataRegionConfiguration().setName("mem2").setPersistenceEnabled(false)));

        return cfg;
    }

    /** {@inheritDoc} */
    @Override protected void beforeTestsStarted() throws Exception {
        super.beforeTestsStarted();

        cleanPersistenceDir();
    }

    /** {@inheritDoc} */
    @Override protected void afterTestsStopped() throws Exception {
        cleanPersistenceDir();
    }

    /**
     * @throws Exception On failed.
     */
    @Test
    public void test() throws Exception {
        // Warmup
        for (int i = 0; i < RESTARTS / 2; ++i) {
            startGrids(NODES);

            U.sleep(500);

            stopAllGrids();
        }

        GridDebug.dumpHeap(HEAP_DUMP_FILE_NAME, true);

        File dumpFile = new File(HEAP_DUMP_FILE_NAME);

        final long size0 = dumpFile.length();

        // Restarts
        for (int i = 0; i < RESTARTS; ++i) {
            startGrids(NODES);

            U.sleep(500);

            stopAllGrids();

            GridDebug.dumpHeap(HEAP_DUMP_FILE_NAME, true);
        }

        GridDebug.dumpHeap(HEAP_DUMP_FILE_NAME, true);

        final float leakSize = (float)(dumpFile.length() - size0) / 1024 / 1024 / NODES / RESTARTS;

        assertTrue("Possible leaks detected. The " + leakSize + "M leaks per node restart after " + RESTARTS
                + " restarts. See the '" + dumpFile.getAbsolutePath() + "'",
            leakSize < ALLOW_LEAK_ON_RESTART_IN_MB);

        // Remove dump if successful.
        dumpFile.delete();
   }
}
