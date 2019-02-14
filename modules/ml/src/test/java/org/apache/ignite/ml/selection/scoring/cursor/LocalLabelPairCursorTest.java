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

package org.apache.ignite.ml.selection.scoring.cursor;

import java.util.HashMap;
import java.util.Map;
import org.apache.ignite.ml.math.primitives.vector.VectorUtils;
import org.apache.ignite.ml.selection.scoring.LabelPair;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Tests for {@link LocalLabelPairCursor}.
 */
public class LocalLabelPairCursorTest {
    /** */
    @Test
    public void testIterate() {
        Map<Integer, Integer> data = new HashMap<>();

        for (int i = 0; i < 1000; i++)
            data.put(i, i);

        LabelPairCursor<Integer> cursor = new LocalLabelPairCursor<>(
            data,
            (k, v) -> v % 2 == 0,
            (k, v) -> VectorUtils.of(v),
            (k, v) -> v,
            vec -> (int)vec.get(0)
        );

        int cnt = 0;
        for (LabelPair<Integer> e : cursor) {
            assertEquals(e.getPrediction(), e.getTruth());
            cnt++;
        }
        assertEquals(500, cnt);
    }
}
