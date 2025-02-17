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

package org.apache.ignite.internal.processors.hadoop.impl;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.io.compress.CompressionInputStream;
import org.apache.hadoop.io.compress.CompressionOutputStream;
import org.apache.hadoop.io.compress.SnappyCodec;
import org.apache.hadoop.io.compress.snappy.SnappyCompressor;
import org.apache.hadoop.util.NativeCodeLoader;
import org.apache.ignite.internal.processors.hadoop.HadoopClassLoader;
import org.apache.ignite.internal.processors.hadoop.HadoopHelperImpl;
import org.apache.ignite.internal.util.typedef.internal.U;
import org.apache.ignite.testframework.junits.common.GridCommonAbstractTest;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Tests isolated Hadoop Snappy codec usage.
 */
@Ignore("https://issues.apache.org/jira/browse/IGNITE-9920")
public class HadoopSnappyTest extends GridCommonAbstractTest {
    /** Length of data. */
    private static final int BYTE_SIZE = 1024 * 50;

    /**
     * Checks Snappy codec usage.
     *
     * @throws Exception On error.
     */
    @Ignore("https://issues.apache.org/jira/browse/IGNITE-9920")
    @Test
    public void testSnappy() throws Throwable {
        // Run Snappy test in default class loader:
        checkSnappy();

        // Run the same in several more class loaders simulating jobs and tasks:
        for (int i = 0; i < 2; i++) {
            ClassLoader hadoopClsLdr = new HadoopClassLoader(null, "cl-" + i, null, new HadoopHelperImpl());

            Class<?> cls = (Class)Class.forName(HadoopSnappyTest.class.getName(), true, hadoopClsLdr);

            assertEquals(hadoopClsLdr, cls.getClassLoader());

            U.invoke(cls, null, "checkSnappy");
        }
    }

    /**
     * Internal check routine.
     *
     * @throws Throwable If failed.
     */
    public static void checkSnappy() throws Throwable {
        try {
            byte[] expBytes = new byte[BYTE_SIZE];
            byte[] actualBytes = new byte[BYTE_SIZE];

            for (int i = 0; i < expBytes.length ; i++)
                expBytes[i] = (byte)ThreadLocalRandom.current().nextInt(16);

            SnappyCodec codec = new SnappyCodec();

            codec.setConf(new Configuration());

            ByteArrayOutputStream baos = new ByteArrayOutputStream();

            try (CompressionOutputStream cos = codec.createOutputStream(baos)) {
                cos.write(expBytes);
                cos.flush();
            }

            try (CompressionInputStream cis = codec.createInputStream(new ByteArrayInputStream(baos.toByteArray()))) {
                int read = cis.read(actualBytes, 0, actualBytes.length);

                assert read == actualBytes.length;
            }

            assert Arrays.equals(expBytes, actualBytes);
        }
        catch (Throwable e) {
            System.out.println("Snappy check failed:");
            System.out.println("### NativeCodeLoader.isNativeCodeLoaded:  " + NativeCodeLoader.isNativeCodeLoaded());
            System.out.println("### SnappyCompressor.isNativeCodeLoaded:  " + SnappyCompressor.isNativeCodeLoaded());

            throw e;
        }
    }
}
