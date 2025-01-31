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
package org.apache.ignite.internal.processors.cache.persistence;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.nio.file.OpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.IgniteLogger;
import org.apache.ignite.IgniteSystemProperties;
import org.apache.ignite.cluster.ClusterNode;
import org.apache.ignite.configuration.CacheConfiguration;
import org.apache.ignite.configuration.DataRegionConfiguration;
import org.apache.ignite.configuration.DataStorageConfiguration;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.internal.GridKernalContext;
import org.apache.ignite.internal.processors.cache.persistence.file.AsyncFileIOFactory;
import org.apache.ignite.internal.processors.cache.persistence.file.FileIO;
import org.apache.ignite.internal.processors.cache.persistence.file.FileIODecorator;
import org.apache.ignite.internal.processors.cache.persistence.file.FileIOFactory;
import org.apache.ignite.internal.processors.cache.persistence.file.FilePageStoreManager;
import org.apache.ignite.logger.NullLogger;
import org.apache.ignite.testframework.ListeningTestLogger;
import org.apache.ignite.testframework.LogListener;
import org.apache.ignite.testframework.junits.WithSystemProperty;
import org.apache.ignite.testframework.junits.common.GridCommonAbstractTest;
import org.junit.Test;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;

/**
 * Test async LFS cleanup during non-BLT node join.
 */
@WithSystemProperty(key = IgniteSystemProperties.IGNITE_BASELINE_AUTO_ADJUST_ENABLED, value = "false")
public class CleanupRestoredCachesSlowTest extends GridCommonAbstractTest implements Serializable {
    /** */
    private static class FilePageStoreManagerChild extends FilePageStoreManager {
        /** */
        static class LongOperationAsyncExecutorChild extends LongOperationAsyncExecutor {
            /** */
            public LongOperationAsyncExecutorChild(String igniteInstanceName, IgniteLogger log) {
                super(igniteInstanceName, log);
            }
        }

        /**
         * @param ctx Kernal context.
         */
        public FilePageStoreManagerChild(GridKernalContext ctx) {
            super(ctx);
        }
    }

    /**
     * FileIO factory, creating {@link SlowFileIO}
     */
    private static class SlowFileIOFactory implements FileIOFactory {
        /** */
        private final AsyncFileIOFactory asyncFileIOFactory = new AsyncFileIOFactory();

        /** {@inheritDoc} */
        @Override public FileIO create(File file, OpenOption... modes) throws IOException {
            FileIO delegate = asyncFileIOFactory.create(file, modes);

            return new SlowFileIO(delegate);
        }
    }

    /**
     * Slow FileIO, adding delay for some methods.
     */
    private static class SlowFileIO extends FileIODecorator implements Serializable {
        /**
         * @param delegate File I/O delegate
         */
        SlowFileIO(FileIO delegate) {
            super(delegate);
        }

        /**
         * Slow close method.
         *
         * @throws IOException if super method failed.
         */
        @Override public void close() throws IOException {
            doSleep(100);

            super.close();
        }
    }

    /**
     * Checks the order of the messages in the log.
     */
    private static class MessageOrderLogListener extends LogListener {
        /** */
        private final LinkedHashSet<String> matchedMessages = new LinkedHashSet<>();

        /** */
        private final List<String> matchesList;

        /** */
        private final boolean doAddDuplicates;

        /** */
        MessageOrderLogListener(List<String> matchesList, boolean doAddDuplicates) {
            this.matchesList = matchesList;

            this.doAddDuplicates = doAddDuplicates;
        }

        /** {@inheritDoc} */
        @Override public boolean check() {
            List<String> list = new ArrayList<>(matchedMessages);

            return list.equals(matchesList);
        }

        /** {@inheritDoc} */
        @Override public void reset() {
            matchedMessages.clear();
        }

        /** {@inheritDoc} */
        @Override public void accept(String s) {
            for (String match : matchesList)
                if (s.matches(match)) {
                    if (doAddDuplicates || !matchedMessages.contains(match))
                        matchedMessages.add(match);

                    break;
                }
        }
    }

    /** */
    private static final String CACHE_NAME = "myCache";

    /** */
    private final LogListener logLsnr = new MessageOrderLogListener(
        Arrays.asList(
            "Cache stores cleanup started asynchronously",
            "Cleanup cache stores .*? cleanFiles=true\\]"
        ),
        false
    );

    /** {@inheritDoc} */
    @Override protected IgniteConfiguration getConfiguration(String igniteInstanceName) throws Exception {
        ListeningTestLogger testLog =
            new ListeningTestLogger(false, super.getConfiguration(igniteInstanceName).getGridLogger());

        testLog.registerListener(logLsnr);

        return super.getConfiguration(igniteInstanceName)
            .setDataStorageConfiguration(
                new DataStorageConfiguration()
                    .setDefaultDataRegionConfiguration(
                        new DataRegionConfiguration()
                            .setPersistenceEnabled(true)
                            .setInitialSize(100 * 1024L * 1024L)
                            .setMaxSize(1024 * 1024L * 1024L)
                            .setMetricsEnabled(true)
                    )
                .setFileIOFactory(new SlowFileIOFactory())
            )
            .setCacheConfiguration(
                new CacheConfiguration()
                    .setName(CACHE_NAME)
                    .setBackups(1)
            )
            .setGridLogger(testLog);
    }

    /** {@inheritDoc} */
    @Override protected void beforeTest() throws Exception {
        cleanPersistenceDir();
    }

    /** {@inheritDoc} */
    @Override protected void afterTest() throws Exception {
        stopAllGrids();

        cleanPersistenceDir();
    }

    /**
     *
     * @throws Exception if failed.
     */
    @Test
    public void testCleanupSlow() throws Exception {
        Ignite ignite = startGrids(2);

        ClusterNode cn0 = grid(0).cluster().localNode();
        ClusterNode cn1 = grid(1).cluster().localNode();

        ignite.cluster().active(true);

        ignite.cluster().baselineAutoAdjustEnabled(false);

        ignite.cluster().setBaselineTopology(asList(cn0, cn1));

        IgniteCache<Object, Object> cache = ignite.getOrCreateCache(CACHE_NAME);

        for (int i = 0; i < 50; i++)
            cache.put(i, new byte[1024]);

        stopGrid(1);

        ignite.cluster().setBaselineTopology(singletonList(cn0));

        startGrid(1);

        assertTrue(logLsnr.check());
    }

    /**
     *
     * @throws Throwable if failed.
     */
    @Test
    public void testLongOperationAsyncExecutor() throws Throwable {
        FilePageStoreManagerChild.LongOperationAsyncExecutorChild executor =
            new FilePageStoreManagerChild.LongOperationAsyncExecutorChild("test", new NullLogger());

        final AtomicInteger ai = new AtomicInteger(1);

        AtomicReference<Throwable> throwable = new AtomicReference<>();

        for (int i = 0; i < 1000; i++) {
            executor.async(() -> {
                ai.set(0);

                doSleep(3);

                try {
                    assertEquals(0, ai.get());
                }
                catch (AssertionError e) {
                    throwable.set(e);
                }

                ai.set(1);
            });

            executor.afterAsyncCompletion(() -> {
                assertEquals(1, ai.get());

                return null;
            });
        }

        if (throwable.get() != null)
            throw throwable.get();
    }
}
