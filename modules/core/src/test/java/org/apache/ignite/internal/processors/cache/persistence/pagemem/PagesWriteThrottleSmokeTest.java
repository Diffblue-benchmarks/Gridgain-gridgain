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
package org.apache.ignite.internal.processors.cache.persistence.pagemem;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.file.OpenOption;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.cache.CacheAtomicityMode;
import org.apache.ignite.cache.CacheWriteSynchronizationMode;
import org.apache.ignite.cache.affinity.rendezvous.RendezvousAffinityFunction;
import org.apache.ignite.configuration.CacheConfiguration;
import org.apache.ignite.configuration.DataRegionConfiguration;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.configuration.DataStorageConfiguration;
import org.apache.ignite.configuration.WALMode;
import org.apache.ignite.internal.IgniteEx;
import org.apache.ignite.internal.IgniteInternalFuture;
import org.apache.ignite.internal.processors.cache.persistence.file.FileIO;
import org.apache.ignite.internal.processors.cache.persistence.file.FileIODecorator;
import org.apache.ignite.internal.processors.cache.persistence.file.FileIOFactory;
import org.apache.ignite.internal.processors.cache.persistence.file.RandomAccessFileIOFactory;
import org.apache.ignite.internal.processors.cache.ratemetrics.HitRateMetrics;
import org.apache.ignite.internal.util.typedef.internal.S;
import org.apache.ignite.internal.util.typedef.internal.U;
import org.apache.ignite.testframework.GridTestUtils;
import org.apache.ignite.testframework.junits.common.GridCommonAbstractTest;
import org.junit.Test;

/**
 *
 */
public class PagesWriteThrottleSmokeTest extends GridCommonAbstractTest {
    /** Slow checkpoint enabled. */
    private static final AtomicBoolean slowCheckpointEnabled = new AtomicBoolean(true);

    /** Cache name. */
    private static final String CACHE_NAME = "cache1";

    /** {@inheritDoc} */
    @Override protected IgniteConfiguration getConfiguration(String gridName) throws Exception {
        IgniteConfiguration cfg = super.getConfiguration(gridName);

        DataStorageConfiguration dbCfg = new DataStorageConfiguration()
            .setDefaultDataRegionConfiguration(new DataRegionConfiguration()
                .setMaxSize(400L * 1024 * 1024)
                .setCheckpointPageBufferSize(200L * 1000 * 1000)
                .setName("dfltDataRegion")
                .setMetricsEnabled(true)
                .setPersistenceEnabled(true))
            .setWalMode(WALMode.BACKGROUND)
            .setCheckpointFrequency(20_000)
            .setWriteThrottlingEnabled(true)
            .setCheckpointThreads(1)
            .setFileIOFactory(new SlowCheckpointFileIOFactory());

        cfg.setDataStorageConfiguration(dbCfg);

        CacheConfiguration ccfg1 = new CacheConfiguration();

        ccfg1.setName(CACHE_NAME);
        ccfg1.setAtomicityMode(CacheAtomicityMode.ATOMIC);
        ccfg1.setWriteSynchronizationMode(CacheWriteSynchronizationMode.PRIMARY_SYNC);
        ccfg1.setAffinity(new RendezvousAffinityFunction(false, 64));

        cfg.setCacheConfiguration(ccfg1);

        cfg.setConsistentId(gridName);

        return cfg;
    }

    /** {@inheritDoc} */
    @Override protected void beforeTest() throws Exception {
        super.beforeTest();

        deleteWorkFiles();

        slowCheckpointEnabled.set(true);
    }

    /** {@inheritDoc} */
    @Override protected void afterTest() throws Exception {
        super.afterTest();

        deleteWorkFiles();
    }

    /** {@inheritDoc} */
    @Override protected long getTestTimeout() {
        return 6 * 60 * 1000;
    }

    /**
     * @throws Exception if failed.
     */
    @Test
    public void testThrottle() throws Exception {
        startGrids(2).active(true);

        try {
            Ignite ig = ignite(0);

            final int keyCnt = 2_000_000;

            final AtomicBoolean run = new AtomicBoolean(true);

            final AtomicBoolean zeroDropdown = new AtomicBoolean(false);

            final HitRateMetrics putRate10secs = new HitRateMetrics(10_000, 20);

            final HitRateMetrics putRate1sec = new HitRateMetrics(1_000, 20);

            GridTestUtils.runAsync(new Runnable() {
                @Override public void run() {
                    try {
                        Thread.sleep(5000);

                        while (run.get()) {
                            System.out.println(
                                "Put rate over last 10 seconds: " + (putRate10secs.getRate() / 10) +
                                    " puts/sec, over last 1 second: " + putRate1sec.getRate());

                            if (putRate10secs.getRate() == 0) {
                                zeroDropdown.set(true);

                                run.set(false);
                            }

                            Thread.sleep(1000);
                        }
                    }
                    catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    finally {
                        run.set(false);
                    }
                }
            }, "rate-checker");

            final IgniteCache<Integer, TestValue> cache = ig.getOrCreateCache(CACHE_NAME);

            GridTestUtils.runAsync(new Runnable() {
                @Override public void run() {
                    long startTs = System.currentTimeMillis();

                    for (int i = 0; i < keyCnt * 10 && System.currentTimeMillis() - startTs < 3 * 60 * 1000; i++) {
                        if (!run.get())
                            break;

                        cache.put(ThreadLocalRandom.current().nextInt(keyCnt), new TestValue(ThreadLocalRandom.current().nextInt(),
                            ThreadLocalRandom.current().nextInt()));

                        putRate10secs.onHit();

                        putRate1sec.onHit();
                    }

                    run.set(false);
                }
            }, "loader");

            while (run.get())
                LockSupport.parkNanos(10_000);

            if (zeroDropdown.get()) {
                slowCheckpointEnabled.set(false);

                IgniteInternalFuture cpFut1 = ((IgniteEx)ignite(0)).context().cache().context().database()
                    .wakeupForCheckpoint("test");

                IgniteInternalFuture cpFut2 = ((IgniteEx)ignite(1)).context().cache().context().database()
                    .wakeupForCheckpoint("test");

                cpFut1.get();

                cpFut2.get();

                fail("Put rate degraded to zero for at least 10 seconds");
            }
        }
        finally {
            stopAllGrids();
        }
    }

    /**
     *
     */
    private static class TestValue implements Serializable {
        /** */
        private final int v1;

        /** */
        private final int v2;

        /** */
        @SuppressWarnings("unused")
        private byte[] payload = new byte[400 + ThreadLocalRandom.current().nextInt(20)];

        /**
         * @param v1 Value 1.
         * @param v2 Value 2.
         */
        private TestValue(int v1, int v2) {
            this.v1 = v1;
            this.v2 = v2;
        }

        /** {@inheritDoc} */
        @Override public boolean equals(Object o) {
            if (this == o)
                return true;

            if (o == null || getClass() != o.getClass())
                return false;

            TestValue val = (TestValue)o;

            return v1 == val.v1 && v2 == val.v2;
        }

        /** {@inheritDoc} */
        @Override public int hashCode() {
            int res = v1;

            res = 31 * res + v2;

            return res;
        }

        /** {@inheritDoc} */
        @Override public String toString() {
            return S.toString(TestValue.class, this);
        }
    }

    /**
     * @throws Exception If failed.
     */
    private void deleteWorkFiles() throws Exception {
        cleanPersistenceDir();

        U.delete(U.resolveWorkDirectory(U.defaultWorkDirectory(), "snapshot", false));
    }

    /**
     * Create File I/O that emulates poor checkpoint write speed.
     */
    private static class SlowCheckpointFileIOFactory implements FileIOFactory {
        /** Serial version uid. */
        private static final long serialVersionUID = 0L;

        /** Delegate factory. */
        private final FileIOFactory delegateFactory = new RandomAccessFileIOFactory();

        /** {@inheritDoc} */
        @Override public FileIO create(File file, OpenOption... openOption) throws IOException {
            final FileIO delegate = delegateFactory.create(file, openOption);

            return new FileIODecorator(delegate) {
                @Override public int write(ByteBuffer srcBuf) throws IOException {
                    if (slowCheckpointEnabled.get() && Thread.currentThread().getName().contains("checkpoint"))
                        LockSupport.parkNanos(5_000_000);

                    return delegate.write(srcBuf);
                }

                @Override public int write(ByteBuffer srcBuf, long position) throws IOException {
                    if (slowCheckpointEnabled.get() && Thread.currentThread().getName().contains("checkpoint"))
                        LockSupport.parkNanos(5_000_000);

                    return delegate.write(srcBuf, position);
                }

                @Override public int write(byte[] buf, int off, int len) throws IOException {
                    if (slowCheckpointEnabled.get() && Thread.currentThread().getName().contains("checkpoint"))
                        LockSupport.parkNanos(5_000_000);

                    return delegate.write(buf, off, len);
                }

                /** {@inheritDoc} */
                @Override public MappedByteBuffer map(int sizeBytes) throws IOException {
                    return delegate.map(sizeBytes);
                }
            };
        }
    }
}
