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

package org.apache.ignite.internal.client.thin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import org.apache.ignite.internal.binary.streams.BinaryInputStream;
import org.apache.ignite.internal.binary.streams.BinaryOutputStream;

/**
 * Fields query pager.
 */
class ClientFieldsQueryPager extends GenericQueryPager<List<?>> implements FieldsQueryPager<List<?>> {
    /** Keep binary. */
    private final boolean keepBinary;

    /** Field names. */
    private List<String> fieldNames = new ArrayList<>();

    /** Serializer/deserializer. */
    private final ClientUtils serDes;

    /** Constructor. */
    ClientFieldsQueryPager(
        ReliableChannel ch,
        ClientOperation qryOp,
        ClientOperation pageQryOp,
        Consumer<BinaryOutputStream> qryWriter,
        boolean keepBinary,
        ClientBinaryMarshaller marsh
    ) {
        super(ch, qryOp, pageQryOp, qryWriter);

        this.keepBinary = keepBinary;

        serDes = new ClientUtils(marsh);
    }

    /** {@inheritDoc} */
    @Override Collection<List<?>> readEntries(BinaryInputStream in) {
        if (!hasFirstPage())
            fieldNames = new ArrayList<>(ClientUtils.collection(in, ignored -> (String)serDes.readObject(in, keepBinary)));

        int rowCnt = in.readInt();

        Collection<List<?>> res = new ArrayList<>(rowCnt);

        for (int r = 0; r < rowCnt; r++) {
            List<?> row = new ArrayList<>(fieldNames.size());

            for (int f = 0; f < fieldNames.size(); f++)
                row.add(serDes.readObject(in, keepBinary));

            res.add(row);
        }

        return res;
    }

    /** {@inheritDoc} */
    @Override public List<String> getFieldNames() {
        return fieldNames;
    }
}
