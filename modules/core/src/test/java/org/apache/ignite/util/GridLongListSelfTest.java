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

package org.apache.ignite.util;

import org.apache.ignite.internal.util.GridLongList;
import org.junit.Test;

import static org.apache.ignite.internal.util.GridLongList.asList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 *
 */
public class GridLongListSelfTest {
    /**
     * @throws Exception If failed.
     */
    @SuppressWarnings("ZeroLengthArrayAllocation")
    @Test
    public void testCopyWithout() throws Exception {
        assertCopy(
            new GridLongList(new long[] {}),
            new GridLongList(new long[] {}));

        assertCopy(
            new GridLongList(new long[] {}),
            new GridLongList(new long[] {1}));

        assertCopy(
            new GridLongList(new long[] {1}),
            new GridLongList(new long[] {}));

        assertCopy(
            new GridLongList(new long[] {1, 2, 3}),
            new GridLongList(new long[] {4, 5, 6}));

        assertCopy(
            new GridLongList(new long[] {1, 2, 3}),
            new GridLongList(new long[] {1, 2, 3}));

        assertCopy(
            new GridLongList(new long[] {1, 2, 3, 4, 5, 1}),
            new GridLongList(new long[] {1, 1}));

        assertCopy(
            new GridLongList(new long[] {1, 1, 1, 2, 3, 4, 5, 1, 1, 1}),
            new GridLongList(new long[] {1, 1}));

        assertCopy(
            new GridLongList(new long[] {1, 2, 3}),
            new GridLongList(new long[] {1, 1, 2, 2, 3, 3}));
    }

    /**
     *
     */
    @Test
    public void testTruncate() {
        GridLongList list = asList(1, 2, 3, 4, 5, 6, 7, 8);

        list.truncate(4, true);

        assertEquals(asList(1, 2, 3, 4), list);

        list.truncate(2, false);

        assertEquals(asList(3, 4), list);

        list = new GridLongList();

        list.truncate(0, false);
        list.truncate(0, true);

        assertEquals(new GridLongList(), list);
    }

    /**
     * Assert {@link GridLongList#copyWithout(GridLongList)} on given lists.
     *
     * @param lst Source lists.
     * @param rmv Exclude list.
     */
    private void assertCopy(GridLongList lst, GridLongList rmv) {
        GridLongList res = lst.copyWithout(rmv);

        for (int i = 0; i < lst.size(); i++) {
            long v = lst.get(i);

            if (rmv.contains(v))
                assertFalse(res.contains(v));
            else
                assertTrue(res.contains(v));
        }
    }

    /**
     *
     */
    @Test
    public void testRemove() {
        GridLongList list = asList(1,2,3,4,5,6);

        assertEquals(2, list.removeValue(0, 3));
        assertEquals(asList(1,2,4,5,6), list);

        assertEquals(-1, list.removeValue(1, 1));
        assertEquals(-1, list.removeValue(0, 3));

        assertEquals(4, list.removeValue(0, 6));
        assertEquals(asList(1,2,4,5), list);

        assertEquals(2, list.removeIndex(1));
        assertEquals(asList(1,4,5), list);

        assertEquals(1, list.removeIndex(0));
        assertEquals(asList(4,5), list);
    }

    /**
     *
     */
    @Test
    public void testSort() {
        assertEquals(new GridLongList(), new GridLongList().sort());
        assertEquals(asList(1), asList(1).sort());
        assertEquals(asList(1, 2), asList(2, 1).sort());
        assertEquals(asList(1, 2, 3), asList(2, 1, 3).sort());

        GridLongList list = new GridLongList();

        list.add(4);
        list.add(3);
        list.add(5);
        list.add(1);

        assertEquals(asList(1, 3, 4, 5), list.sort());

        list.add(0);

        assertEquals(asList(1, 3, 4, 5, 0), list);
        assertEquals(asList(0, 1, 3, 4, 5), list.sort());
    }

    /**
     *
     */
    @Test
    public void testArray() {
        GridLongList list = new GridLongList();

        long[] array = list.array();

        assertNotNull(array);

        assertEquals(0, array.length);

        list.add(1L);

        array = list.array();

        assertNotNull(array);

        assertEquals(1, array.length);

        assertEquals(1L, array[0]);
    }
}
