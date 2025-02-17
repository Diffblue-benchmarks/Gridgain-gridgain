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

package org.apache.ignite.internal.processors.igfs;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;
import org.apache.ignite.igfs.IgfsMode;
import org.junit.Test;

import static org.apache.ignite.igfs.IgfsMode.DUAL_SYNC;
import static org.apache.ignite.igfs.IgfsMode.PRIMARY;
import static org.apache.ignite.igfs.IgfsMode.PROXY;

/**
 * {@link IgfsAttributes} test case.
 */
public class IgfsAttributesSelfTest extends IgfsCommonAbstractTest {
    /**
     * @throws Exception If failed.
     */
    @Test
    public void testSerialization() throws Exception {
        Map<String, IgfsMode> pathModes = new HashMap<>();

        pathModes.put("path1", PRIMARY);
        pathModes.put("path2", PROXY);

        IgfsAttributes attrs = new IgfsAttributes("testIgfsName", 513000, 888, "meta", "data", DUAL_SYNC,
            pathModes, true);

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutput os = new ObjectOutputStream(bos);

        os.writeObject(attrs);
        os.close();

        IgfsAttributes deserializedAttrs = (IgfsAttributes)new ObjectInputStream(
            new ByteArrayInputStream(bos.toByteArray())).readObject();

        assertTrue(eq(attrs, deserializedAttrs));
    }

    /**
     * @param attr1 Attributes 1.
     * @param attr2 Attributes 2.
     * @return Whether equals or not.
     * @throws Exception In case of error.
     */
    private boolean eq(IgfsAttributes attr1, IgfsAttributes attr2) throws Exception {
        assert attr1 != null;
        assert attr2 != null;

        for (Field f : IgfsAttributes.class.getDeclaredFields()) {
            f.setAccessible(true);

            if (!Modifier.isStatic(f.getModifiers()) && !f.get(attr1).equals(f.get(attr2)))
                return false;
        }

        return true;
    }
}
