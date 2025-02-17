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

package org.apache.ignite.igfs;

import org.apache.ignite.IgniteFileSystem;
import org.junit.Test;

/**
 * Tests coordinator transfer from one node to other.
 */
public class IgfsFragmentizerTopologySelfTest extends IgfsFragmentizerAbstractSelfTest {
    /**
     * @throws Exception If failed.
     */
    @Test
    public void testCoordinatorLeave() throws Exception {
        stopGrid(0);

        // Now node 1 should be coordinator.
        try {
            IgfsPath path = new IgfsPath("/someFile");

            IgniteFileSystem igfs = grid(1).fileSystem("igfs");

            try (IgfsOutputStream out = igfs.create(path, true)) {
                for (int i = 0; i < 10 * IGFS_GROUP_SIZE; i++)
                    out.write(new byte[IGFS_BLOCK_SIZE]);
            }

            awaitFileFragmenting(1, path);
        }
        finally {
            startGrid(0);
        }
    }
}
