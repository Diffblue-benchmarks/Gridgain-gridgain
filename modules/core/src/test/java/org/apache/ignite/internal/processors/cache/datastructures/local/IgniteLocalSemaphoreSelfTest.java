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

package org.apache.ignite.internal.processors.cache.datastructures.local;

import java.util.concurrent.Callable;
import org.apache.ignite.IgniteSemaphore;
import org.apache.ignite.cache.CacheMode;
import org.apache.ignite.internal.IgniteInternalFuture;
import org.apache.ignite.internal.processors.cache.datastructures.IgniteSemaphoreAbstractSelfTest;
import org.apache.ignite.testframework.GridTestUtils;
import org.jetbrains.annotations.Nullable;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static java.util.concurrent.TimeUnit.MINUTES;
import static org.apache.ignite.cache.CacheMode.LOCAL;

/**
 *
 */
@RunWith(JUnit4.class)
public class IgniteLocalSemaphoreSelfTest extends IgniteSemaphoreAbstractSelfTest {
    /** {@inheritDoc} */
    @Override protected CacheMode atomicsCacheMode() {
        return LOCAL;
    }

    /** {@inheritDoc} */
    @Override protected int gridCount() {
        return 1;
    }

    /** {@inheritDoc} */
    @Test
    @Override public void testSemaphore() throws Exception {
        // Test main functionality.
        IgniteSemaphore semaphore = grid(0).semaphore("semaphore", -2, false, true);

        assertNotNull(semaphore);

        assertEquals(-2, semaphore.availablePermits());

        IgniteInternalFuture<?> fut = GridTestUtils.runMultiThreadedAsync(
            new Callable<Object>() {
                @Nullable @Override public Object call() throws Exception {
                    IgniteSemaphore semaphore = grid(0).semaphore("semaphore", -2, false, true);

                    assert semaphore != null && semaphore.availablePermits() == -2;

                    info("Thread is going to wait on semaphore: " + Thread.currentThread().getName());

                    assert semaphore.tryAcquire(1, 1, MINUTES);

                    info("Thread is again runnable: " + Thread.currentThread().getName());

                    semaphore.release();

                    return null;
                }
            },
            THREADS_CNT,
            "test-thread"
        );

        Thread.sleep(3000);

        assert semaphore.availablePermits() == -2;

        semaphore.release(2);

        assert semaphore.availablePermits() == 0;

        semaphore.release();

        // Ensure there are no hangs.
        fut.get();

        // Test operations on removed latch.
        IgniteSemaphore semaphore0 = grid(0).semaphore("semaphore", 0, false, false);

        assertNotNull(semaphore0);

        semaphore0.close();

        checkRemovedSemaphore(semaphore0);
    }
}
