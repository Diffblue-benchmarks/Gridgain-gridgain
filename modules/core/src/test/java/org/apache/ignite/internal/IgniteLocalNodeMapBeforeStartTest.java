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

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import org.apache.ignite.Ignite;
import org.apache.ignite.Ignition;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.lifecycle.LifecycleBean;
import org.apache.ignite.lifecycle.LifecycleEventType;
import org.apache.ignite.resources.IgniteInstanceResource;
import org.apache.ignite.testframework.junits.common.GridCommonAbstractTest;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static org.apache.ignite.lifecycle.LifecycleEventType.AFTER_NODE_START;
import static org.apache.ignite.lifecycle.LifecycleEventType.AFTER_NODE_STOP;
import static org.apache.ignite.lifecycle.LifecycleEventType.BEFORE_NODE_START;
import static org.apache.ignite.lifecycle.LifecycleEventType.BEFORE_NODE_STOP;

/**
 *
 */
@RunWith(JUnit4.class)
public class IgniteLocalNodeMapBeforeStartTest extends GridCommonAbstractTest {
    /**
     * @throws Exception If failed.
     */
    @Test
    public void testNodeLocalMapFromLifecycleBean() throws Exception {
        IgniteConfiguration cfg = getConfiguration(getTestIgniteInstanceName(0));

        LifecycleBeanTest lifecycleBean = new LifecycleBeanTest();

        // Provide lifecycle bean to configuration.
        cfg.setLifecycleBeans(lifecycleBean);

        try (Ignite ignite  = Ignition.start(cfg)) {
            // No-op.
        }

        assertTrue(lifecycleBean.evtQueue.size() == 4);
        assertTrue(lifecycleBean.evtQueue.poll() == BEFORE_NODE_START);
        assertTrue(lifecycleBean.evtQueue.poll() == AFTER_NODE_START);
        assertTrue(lifecycleBean.evtQueue.poll() == BEFORE_NODE_STOP);
        assertTrue(lifecycleBean.evtQueue.poll() == AFTER_NODE_STOP);
    }

    /**
     * Simple {@link LifecycleBean} implementation.
     */
    private static class LifecycleBeanTest implements LifecycleBean {
        /** Auto-inject ignite instance. */
        @IgniteInstanceResource
        private Ignite ignite;

        /** Event queue. */
        ConcurrentLinkedQueue<LifecycleEventType> evtQueue = new ConcurrentLinkedQueue<>();

        /** {@inheritDoc} */
        @Override public void onLifecycleEvent(LifecycleEventType evt) {
            evtQueue.add(evt);

            // check nodeLocalMap is not locked
            ConcurrentMap map = ignite.cluster().nodeLocalMap();

            assertNotNull(map);
        }
    }
}
