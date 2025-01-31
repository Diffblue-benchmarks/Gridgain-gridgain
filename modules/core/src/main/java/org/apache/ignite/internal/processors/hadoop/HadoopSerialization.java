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

package org.apache.ignite.internal.processors.hadoop;

import java.io.DataInput;
import java.io.DataOutput;
import org.apache.ignite.IgniteCheckedException;
import org.jetbrains.annotations.Nullable;

/**
 * Hadoop serialization. Not thread safe object, must be created for each thread or correctly synchronized.
 */
public interface HadoopSerialization extends AutoCloseable {
    /**
     * Writes the given object to output.
     *
     * @param out Output.
     * @param obj Object to serialize.
     * @throws IgniteCheckedException If failed.
     */
    public void write(DataOutput out, Object obj) throws IgniteCheckedException;

    /**
     * Reads object from the given input optionally reusing given instance.
     *
     * @param in Input.
     * @param obj Object.
     * @return New object or reused instance.
     * @throws IgniteCheckedException If failed.
     */
    public Object read(DataInput in, @Nullable Object obj) throws IgniteCheckedException;

    /**
     * Finalise the internal objects.
     *
     * @throws IgniteCheckedException If failed.
     */
    @Override public void close() throws IgniteCheckedException;
}