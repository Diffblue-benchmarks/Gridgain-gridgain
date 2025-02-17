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

package org.apache.ignite.ml.dataset.impl.local;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.ignite.ml.TestUtils;
import org.apache.ignite.ml.dataset.PartitionContextBuilder;
import org.apache.ignite.ml.dataset.PartitionDataBuilder;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Tests for {@link LocalDatasetBuilder}.
 */
public class LocalDatasetBuilderTest {
    /** Tests {@code build()} method. */
    @Test
    public void testBuild() {
        Map<Integer, Integer> data = new HashMap<>();
        for (int i = 0; i < 100; i++)
            data.put(i, i);

        LocalDatasetBuilder<Integer, Integer> builder = new LocalDatasetBuilder<>(data, 10);

        LocalDataset<Serializable, TestPartitionData> dataset = buildDataset(builder);

        assertEquals(10, dataset.getCtx().size());
        assertEquals(10, dataset.getData().size());

        AtomicLong cnt = new AtomicLong();

        dataset.compute((partData, env) -> {
           cnt.incrementAndGet();

           int[] arr = partData.data;

           assertEquals(10, arr.length);

           for (int i = 0; i < 10; i++)
               assertEquals(env.partition() * 10 + i, arr[i]);
        });

        assertEquals(10, cnt.intValue());
    }

    /** Tests {@code build()} method with predicate. */
    @Test
    public void testBuildWithPredicate() {
        Map<Integer, Integer> data = new HashMap<>();
        for (int i = 0; i < 100; i++)
            data.put(i, i);

        LocalDatasetBuilder<Integer, Integer> builder = new LocalDatasetBuilder<>(data, (k, v) -> k % 2 == 0,10);

        LocalDataset<Serializable, TestPartitionData> dataset = buildDataset(builder);

        AtomicLong cnt = new AtomicLong();

        dataset.compute((partData, env) -> {
            cnt.incrementAndGet();

            int[] arr = partData.data;

            assertEquals(5, arr.length);

            for (int i = 0; i < 5; i++)
                assertEquals((env.partition() * 5 + i) * 2, arr[i]);
        });

        assertEquals(10, cnt.intValue());
    }

    /** */
    private LocalDataset<Serializable, TestPartitionData> buildDataset(
        LocalDatasetBuilder<Integer, Integer> builder) {
        PartitionContextBuilder<Integer, Integer, Serializable> partCtxBuilder = (env, upstream, upstreamSize) -> null;

        PartitionDataBuilder<Integer, Integer, Serializable, TestPartitionData> partDataBuilder
            = (env, upstream, upstreamSize, ctx) -> {
            int[] arr = new int[Math.toIntExact(upstreamSize)];

            int ptr = 0;
            while (upstream.hasNext())
                arr[ptr++] = upstream.next().getValue();

            return new TestPartitionData(arr);
        };

        return builder.build(
            TestUtils.testEnvBuilder(),
            partCtxBuilder.andThen(x -> null),
            partDataBuilder.andThen((x, y) -> x)
        );
    }

    /**
     * Test partition {@code data}.
     */
    private static class TestPartitionData implements AutoCloseable {
        /** Data. */
        private int[] data;

        /**
         * Constructs a new test partition data instance.
         *
         * @param data Data.
         */
        TestPartitionData(int[] data) {
            this.data = data;
        }

        /** {@inheritDoc} */
        @Override public void close() {
            // Do nothing, GC will clean up.
        }
    }
}
