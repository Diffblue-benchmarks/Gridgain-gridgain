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

import java.io.Externalizable;
import java.util.Random;
import java.util.concurrent.Callable;
import org.apache.ignite.IgniteCheckedException;
import org.apache.ignite.lang.IgniteUuid;
import org.apache.ignite.marshaller.Marshaller;
import org.apache.ignite.marshaller.MarshallerContextTestImpl;
import org.jetbrains.annotations.Nullable;
import org.junit.Test;

/**
 * {@link IgfsEntryInfo} test case.
 */
public class IgfsFileInfoSelfTest extends IgfsCommonAbstractTest {
    /** Marshaller to test {@link Externalizable} interface. */
    private final Marshaller marshaller;

    /** Ctor. */
    public IgfsFileInfoSelfTest() throws IgniteCheckedException {
        marshaller = createStandaloneBinaryMarshaller();
    }


    /**
     * Test node info serialization.
     *
     * @throws Exception If failed.
     */
    @Test
    public void testSerialization() throws Exception {
        marshaller.setContext(new MarshallerContextTestImpl());

        multithreaded(new Callable<Object>() {
            private final Random rnd = new Random();

            @Nullable @Override public Object call() throws IgniteCheckedException {
                testSerialization(IgfsUtils.createDirectory(IgniteUuid.randomUuid()));

                return null;
            }
        }, 20);
    }

    /**
     * Test node info serialization.
     *
     * @param info Node info to test serialization for.
     * @throws IgniteCheckedException If failed.
     */
    public void testSerialization(IgfsEntryInfo info) throws IgniteCheckedException {
        assertEquals(info, mu(info));
    }

    /**
     * Marshal/unmarshal object.
     *
     * @param obj Object to marshal/unmarshal.
     * @return Marshalled and then unmarshalled object.
     * @throws IgniteCheckedException In case of any marshalling exception.
     */
    private <T> T mu(T obj) throws IgniteCheckedException {
        return marshaller.unmarshal(marshaller.marshal(obj), null);
    }
}
