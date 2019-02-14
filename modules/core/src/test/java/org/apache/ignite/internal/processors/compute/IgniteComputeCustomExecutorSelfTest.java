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

package org.apache.ignite.internal.processors.compute;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.apache.ignite.IgniteCompute;
import org.apache.ignite.IgniteException;
import org.apache.ignite.compute.ComputeJob;
import org.apache.ignite.compute.ComputeJobAdapter;
import org.apache.ignite.compute.ComputeJobResult;
import org.apache.ignite.compute.ComputeTaskSplitAdapter;
import org.apache.ignite.configuration.CacheConfiguration;
import org.apache.ignite.configuration.ExecutorConfiguration;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.lang.IgniteCallable;
import org.apache.ignite.lang.IgniteClosure;
import org.apache.ignite.lang.IgniteReducer;
import org.apache.ignite.lang.IgniteRunnable;
import org.apache.ignite.testframework.junits.common.GridCommonAbstractTest;
import org.jetbrains.annotations.Nullable;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests custom executor named pools.
 *
 * https://issues.apache.org/jira/browse/IGNITE-4699
 */
@RunWith(JUnit4.class)
public class IgniteComputeCustomExecutorSelfTest extends GridCommonAbstractTest {
    /** */
    private static final int GRID_CNT = 2;

    /** */
    private static final String EXEC_NAME0 = "executor_0";

    /** */
    private static final String EXEC_NAME1 = "executor_1";

    /** */
    private static final String CACHE_NAME = "testcache";

    /** {@inheritDoc} */
    @Override protected IgniteConfiguration getConfiguration(String igniteInstanceName) throws Exception {
        IgniteConfiguration cfg = super.getConfiguration(igniteInstanceName);

        cfg.setExecutorConfiguration(createExecConfiguration(EXEC_NAME0), createExecConfiguration(EXEC_NAME1));
        cfg.setPublicThreadPoolSize(1);

        CacheConfiguration ccfg = new CacheConfiguration();
        ccfg.setName(CACHE_NAME);

        cfg.setCacheConfiguration(ccfg);

        return cfg;
    }

    /**
     * @param name Custom executor name.
     * @return Executor configuration.
     */
    private ExecutorConfiguration createExecConfiguration(String name) {
        ExecutorConfiguration exec = new ExecutorConfiguration();

        exec.setName(name);
        exec.setSize(1);

        return exec;
    }

    /** {@inheritDoc} */
    @Override protected void beforeTest() throws Exception {
        startGrids(GRID_CNT);
    }

    /** {@inheritDoc} */
    @Override protected void afterTest() throws Exception {
        stopAllGrids();
    }

    /**
     * @throws Exception If fails.
     */
    @Test
    public void testInvalidCustomExecutor() throws Exception {
        grid(0).compute().withExecutor("invalid").broadcast(new IgniteRunnable() {
            @Override public void run() {
                assertTrue(Thread.currentThread().getName().contains("pub"));
            }
        });
    }

    /**
     * @throws Exception If fails.
     */
    @Test
    public void testAllComputeApiByCustomExecutor() throws Exception {
        IgniteCompute comp = grid(0).compute().withExecutor(EXEC_NAME0);

        comp.affinityRun(CACHE_NAME, primaryKey(grid(1).cache(CACHE_NAME)), new IgniteRunnable() {
            @Override public void run() {
                assertTrue(Thread.currentThread().getName().contains(EXEC_NAME0));
            }
        });

        comp.affinityCall(CACHE_NAME, 0, new IgniteCallable<Object>() {
            @Override public Object call() throws Exception {
                assertTrue(Thread.currentThread().getName().contains(EXEC_NAME0));
                return null;
            }
        });

        comp.broadcast(new IgniteRunnable() {
            @Override public void run() {
                assertTrue(Thread.currentThread().getName().contains(EXEC_NAME0));
            }
        });

        comp.broadcast(new IgniteCallable<Object>() {
            @Override public Object call() throws Exception {
                assertTrue(Thread.currentThread().getName().contains(EXEC_NAME0));
                return null;
            }
        });

        comp.broadcast(new IgniteClosure<Object, Object>() {
            @Override public Object apply(Object o) {
                assertTrue(Thread.currentThread().getName().contains(EXEC_NAME0));
                return null;
            }
        }, 0);

        comp.apply(new IgniteClosure<Object, Object>() {
            @Override public Object apply(Object o) {
                assertTrue(Thread.currentThread().getName().contains(EXEC_NAME0));
                return null;
            }
        }, 0);

        comp.apply(new IgniteClosure<Integer, Object>() {
            @Override public Object apply(Integer o) {
                assertTrue(Thread.currentThread().getName().contains(EXEC_NAME0));
                return null;
            }
        }, Collections.singletonList(0));

        comp.apply(new IgniteClosure<Integer, Object>() {
                       @Override public Object apply(Integer o) {
                           assertTrue(Thread.currentThread().getName().contains(EXEC_NAME0));
                           return null;
                       }
                   }, Collections.singletonList(0),
            new IgniteReducer<Object, Object>() {
                @Override public boolean collect(@Nullable Object o) {
                    return true;
                }

                @Override public Object reduce() {
                    return null;
                }
            });

        List<IgniteCallable<Object>> calls = new ArrayList<>();

        for (int i = 0; i < GRID_CNT * 2; ++i) {
            calls.add(new IgniteCallable<Object>() {
                @Override public Object call() throws Exception {
                    assertTrue(Thread.currentThread().getName().contains(EXEC_NAME0));
                    return null;
                }
            });
        }

        comp.call(calls.get(0));

        comp.call(calls);

        comp.call(calls,
            new IgniteReducer<Object, Object>() {
                @Override public boolean collect(@Nullable Object o) {
                    return true;
                }

                @Override public Object reduce() {
                    return null;
                }
            });

        List<IgniteRunnable> runs = new ArrayList<>();

        for (int i = 0; i < GRID_CNT * 2; ++i) {
            runs.add(new IgniteRunnable() {
                @Override public void run() {
                    assertTrue(Thread.currentThread().getName().contains(EXEC_NAME0));
                }
            });
        }

        comp.run(runs.get(0));

        comp.run(runs);

        comp.execute(TestTask.class, null);
    }

    /**
     * Test task
     */
    static class TestTask extends ComputeTaskSplitAdapter<Object, Object> {
        /** {@inheritDoc} */
        @Override protected Collection<? extends ComputeJob> split(int gridSize, Object arg) throws IgniteException {
            List<ComputeJob> jobs = new ArrayList<>(gridSize * 2);

            for (int i = 0; i < gridSize * 2; ++i) {
                jobs.add(new ComputeJobAdapter() {
                    @Override public Object execute() throws IgniteException {
                        assertTrue(Thread.currentThread().getName().contains(EXEC_NAME0));

                        return null;
                    }
                });
            }

            return jobs;
        }

        /** {@inheritDoc} */
        @Nullable @Override public Object reduce(List<ComputeJobResult> results) throws IgniteException {
            return null;
        }
    }
}
