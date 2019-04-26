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
package org.apache.ignite.internal.processors.cache.eviction.paged;

import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;

/**
 *
 */
class TestObject {
    /** */
    private int b;

    /** */
    private String c;

    /** */
    private int[] arr;

    /**
     * @param intArrSize Int array size.
     */
    public TestObject(int intArrSize) {
        this.b = intArrSize;

        this.c = String.valueOf(2 * intArrSize);

        arr = new int[intArrSize];

        for (int i = 0; i < intArrSize; i++)
            arr[i] = ThreadLocalRandom.current().nextInt();
    }

    /** {@inheritDoc} */
    @Override public boolean equals(Object o) {
        if (this == o)
            return true;

        if (o == null || getClass() != o.getClass())
            return false;

        TestObject testObj = (TestObject)o;

        if (b != testObj.b)
            return false;

        if (c != null ? !c.equals(testObj.c) : testObj.c != null)
            return false;

        return Arrays.equals(arr, testObj.arr);
    }

    /** {@inheritDoc} */
    @Override public int hashCode() {
        int res = b;

        res = 31 * res + (c != null ? c.hashCode() : 0);

        res = 31 * res + Arrays.hashCode(arr);

        return res;
    }
}