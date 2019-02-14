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

package org.apache.ignite.internal.binary.builder;

import org.apache.ignite.internal.binary.GridBinaryMarshaller;
import org.apache.ignite.internal.binary.BinaryWriterExImpl;
import org.apache.ignite.internal.util.typedef.internal.U;
import org.apache.ignite.binary.BinaryInvalidTypeException;

/**
 *
 */
class BinaryObjectArrayLazyValue extends BinaryAbstractLazyValue {
    /** */
    private Object[] lazyValsArr;

    /** */
    private int compTypeId;

    /** */
    private String clsName;

    /**
     * @param reader Reader.
     */
    protected BinaryObjectArrayLazyValue(BinaryBuilderReader reader) {
        super(reader, reader.position() - 1);

        int typeId = reader.readInt();

        if (typeId == GridBinaryMarshaller.UNREGISTERED_TYPE_ID) {
            clsName = reader.readString();

            Class cls;

            try {
                cls = U.forName(reader.readString(), reader.binaryContext().configuration().getClassLoader());
            }
            catch (ClassNotFoundException e) {
                throw new BinaryInvalidTypeException("Failed to load the class: " + clsName, e);
            }

            compTypeId = reader.binaryContext().descriptorForClass(cls, true, false).typeId();
        }
        else {
            compTypeId = typeId;
            clsName = null;
        }

        int size = reader.readInt();

        lazyValsArr = new Object[size];

        for (int i = 0; i < size; i++)
            lazyValsArr[i] = reader.parseValue();
    }

    /** {@inheritDoc} */
    @Override protected Object init() {
        for (int i = 0; i < lazyValsArr.length; i++) {
            if (lazyValsArr[i] instanceof BinaryLazyValue)
                lazyValsArr[i] = ((BinaryLazyValue)lazyValsArr[i]).value();
        }

        return lazyValsArr;
    }

    /** {@inheritDoc} */
    @Override public void writeTo(BinaryWriterExImpl writer, BinaryBuilderSerializer ctx) {
        if (clsName == null)
            ctx.writeArray(writer, GridBinaryMarshaller.OBJ_ARR, lazyValsArr, compTypeId);
        else
            ctx.writeArray(writer, GridBinaryMarshaller.OBJ_ARR, lazyValsArr, clsName);
    }
}
