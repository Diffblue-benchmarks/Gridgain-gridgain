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

package org.apache.ignite.platform;

import java.util.ArrayList;
import java.util.HashMap;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.IgniteException;
import org.apache.ignite.binary.BinaryObject;
import org.apache.ignite.cache.affinity.AffinityKey;
import org.apache.ignite.cluster.ClusterNode;
import org.apache.ignite.compute.ComputeJob;
import org.apache.ignite.compute.ComputeJobAdapter;
import org.apache.ignite.compute.ComputeJobResult;
import org.apache.ignite.compute.ComputeTaskAdapter;
import org.apache.ignite.internal.util.typedef.F;
import org.apache.ignite.resources.IgniteInstanceResource;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Test task producing result without any arguments.
 */
public class PlatformComputeEchoTask extends ComputeTaskAdapter<Integer, Object> {
    /** Type: NULL. */
    private static final int TYPE_NULL = 0;

    /** Type: byte. */
    private static final int TYPE_BYTE = 1;

    /** Type: bool. */
    private static final int TYPE_BOOL = 2;

    /** Type: short. */
    private static final int TYPE_SHORT = 3;

    /** Type: char. */
    private static final int TYPE_CHAR = 4;

    /** Type: int. */
    private static final int TYPE_INT = 5;

    /** Type: long. */
    private static final int TYPE_LONG = 6;

    /** Type: float. */
    private static final int TYPE_FLOAT = 7;

    /** Type: double. */
    private static final int TYPE_DOUBLE = 8;

    /** Type: array. */
    private static final int TYPE_ARRAY = 9;

    /** Type: collection. */
    private static final int TYPE_COLLECTION = 10;

    /** Type: map. */
    private static final int TYPE_MAP = 11;

    /** Type: binary object which exists in all platforms. */
    private static final int TYPE_BINARY = 12;

    /** Type: binary object which exists only in Java. */
    private static final int TYPE_BINARY_JAVA = 13;

    /** Type: object array. */
    private static final int TYPE_OBJ_ARRAY = 14;

    /** Type: binary object array. */
    private static final int TYPE_BINARY_ARRAY = 15;

    /** Type: enum. */
    private static final int TYPE_ENUM = 16;

    /** Type: enum array. */
    private static final int TYPE_ENUM_ARRAY = 17;

    /** Type: enum array. */
    private static final int TYPE_ENUM_FIELD = 18;

    /** Type: enum array. */
    private static final int TYPE_AFFINITY_KEY = 19;

    /** Type: enum from cache. */
    private static final int TYPE_ENUM_FROM_CACHE = 20;

    /** Type: enum array from cache. */
    private static final int TYPE_ENUM_ARRAY_FROM_CACHE = 21;

    /** Type: ignite uuid. */
    private static final int TYPE_IGNITE_UUID = 22;

    /** Type: binary enum. */
    private static final int TYPE_BINARY_ENUM = 23;

    /** Default cache name. */
    public static final String DEFAULT_CACHE_NAME = "default";

    /** {@inheritDoc} */
    @Nullable @Override public Map<? extends ComputeJob, ClusterNode> map(List<ClusterNode> subgrid,
        @Nullable Integer arg) {
        return Collections.singletonMap(new EchoJob(arg), F.first(subgrid));
    }

    /** {@inheritDoc} */
    @Nullable @Override public Object reduce(List<ComputeJobResult> results) {
        return results.get(0).getData();
    }

    /**
     * Job.
     */
    private static class EchoJob extends ComputeJobAdapter {
        /** Type. */
        private Integer type;

        /** Ignite. */
        @IgniteInstanceResource
        private Ignite ignite;

        /**
         * Constructor.
         *
         * @param type Result type.
         */
        private EchoJob(Integer type) {
            this.type = type;
        }

        /** {@inheritDoc} */
        @Nullable @Override public Object execute() {
            switch (type) {
                case TYPE_NULL:
                    return null;

                case TYPE_BYTE:
                    return (byte)1;

                case TYPE_BOOL:
                    return true;

                case TYPE_SHORT:
                    return (short)1;

                case TYPE_CHAR:
                    return (char)1;

                case TYPE_INT:
                    return 1;

                case TYPE_LONG:
                    return 1L;

                case TYPE_FLOAT:
                    return (float)1;

                case TYPE_DOUBLE:
                    return (double)1;

                case TYPE_ARRAY:
                    return new int[] { 1 };

                case TYPE_COLLECTION:
                    return new ArrayList<>(Collections.singletonList(1));

                case TYPE_MAP:
                    return new HashMap<>(Collections.singletonMap(1, 1));

                case TYPE_BINARY:
                    Integer field = (Integer) ignite.cache(DEFAULT_CACHE_NAME).get(TYPE_BINARY);

                    return new PlatformComputeBinarizable(field);

                case TYPE_BINARY_JAVA:
                    return new PlatformComputeJavaBinarizable(1);

                case TYPE_OBJ_ARRAY:
                    return new String[] { "foo", "bar", "baz" };

                case TYPE_BINARY_ARRAY:
                    return new PlatformComputeBinarizable[] {
                        new PlatformComputeBinarizable(1),
                        new PlatformComputeBinarizable(2),
                        new PlatformComputeBinarizable(3)
                    };

                case TYPE_ENUM:
                    return PlatformComputeEnum.BAR;

                case TYPE_ENUM_FROM_CACHE:
                    return ignite.cache(DEFAULT_CACHE_NAME).get(TYPE_ENUM_FROM_CACHE);

                case TYPE_ENUM_ARRAY:
                    return new PlatformComputeEnum[] {
                        PlatformComputeEnum.BAR,
                        PlatformComputeEnum.BAZ,
                        PlatformComputeEnum.FOO
                    };

                case TYPE_ENUM_ARRAY_FROM_CACHE:
                    return ignite.cache(DEFAULT_CACHE_NAME).get(TYPE_ENUM_ARRAY_FROM_CACHE);

                case TYPE_ENUM_FIELD:
                    IgniteCache<Integer, BinaryObject> cache = ignite.cache(DEFAULT_CACHE_NAME).withKeepBinary();
                    BinaryObject obj = cache.get(TYPE_ENUM_FIELD);
                    BinaryObject val = obj.field("interopEnum");

                    return val.deserialize();

                case TYPE_AFFINITY_KEY:
                    return new AffinityKey<>("interopAffinityKey");

                case TYPE_IGNITE_UUID:
                    return ignite.cache(DEFAULT_CACHE_NAME).get(TYPE_IGNITE_UUID);

                case TYPE_BINARY_ENUM: {
                    Map<String, Integer> values = new HashMap<>(2);
                    values.put("JavaFoo", 1);
                    values.put("JavaBar", 2);

                    ignite.binary().registerEnum("JavaDynEnum", values);

                    return ignite.binary().buildEnum("JavaDynEnum", "JavaFoo");
                }

                default:
                    throw new IgniteException("Unknown type: " + type);
            }
        }
    }
}
