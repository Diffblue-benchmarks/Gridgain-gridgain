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
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.util.Arrays;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.serializer.JavaSerialization;
import org.apache.hadoop.io.serializer.WritableSerialization;
import org.apache.ignite.internal.processors.hadoop.HadoopSerialization;
import org.apache.ignite.internal.processors.hadoop.impl.v2.HadoopSerializationWrapper;
import org.apache.ignite.testframework.junits.common.GridCommonAbstractTest;
import org.junit.Test;

/**
 * Test of wrapper of the native serialization.
 */
public class HadoopSerializationWrapperSelfTest extends GridCommonAbstractTest {
    /**
     * Tests read/write of IntWritable via native WritableSerialization.
     * @throws Exception If fails.
     */
    @Test
    public void testIntWritableSerialization() throws Exception {
        HadoopSerialization ser = new HadoopSerializationWrapper(new WritableSerialization(), IntWritable.class);

        ByteArrayOutputStream buf = new ByteArrayOutputStream();

        DataOutput out = new DataOutputStream(buf);

        ser.write(out, new IntWritable(3));
        ser.write(out, new IntWritable(-5));

        assertEquals("[0, 0, 0, 3, -1, -1, -1, -5]", Arrays.toString(buf.toByteArray()));

        DataInput in = new DataInputStream(new ByteArrayInputStream(buf.toByteArray()));

        assertEquals(3, ((IntWritable)ser.read(in, null)).get());
        assertEquals(-5, ((IntWritable)ser.read(in, null)).get());
    }

    /**
     * Tests read/write of Integer via native JavaleSerialization.
     * @throws Exception If fails.
     */
    @Test
    public void testIntJavaSerialization() throws Exception {
        HadoopSerialization ser = new HadoopSerializationWrapper(new JavaSerialization(), Integer.class);

        ByteArrayOutputStream buf = new ByteArrayOutputStream();

        DataOutput out = new DataOutputStream(buf);

        ser.write(out, 3);
        ser.write(out, -5);
        ser.close();

        DataInput in = new DataInputStream(new ByteArrayInputStream(buf.toByteArray()));

        assertEquals(3, ((Integer)ser.read(in, null)).intValue());
        assertEquals(-5, ((Integer)ser.read(in, null)).intValue());
    }
}
