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

package org.apache.ignite.internal.processors.odbc.jdbc;

import org.apache.ignite.binary.BinaryObjectException;
import org.apache.ignite.internal.binary.BinaryReaderExImpl;
import org.apache.ignite.internal.binary.BinaryWriterExImpl;
import org.apache.ignite.internal.processors.odbc.ClientListenerProtocolVersion;
import org.apache.ignite.internal.util.typedef.internal.S;

/**
 * JDBC batch execute result.
 */
public class JdbcBatchExecuteResult extends JdbcResult {
    /** Update counts. */
    private int [] updateCnts;

    /** Batch update error code. */
    private int errCode;

    /** Batch update error message. */
    private String errMsg;

    /**
     * Constructor.
     */
    JdbcBatchExecuteResult() {
        super(BATCH_EXEC);
    }

    /**
     * Constructor for child results.
     * @param type Result type.
     */
    JdbcBatchExecuteResult(byte type) {
        super(type);
    }

    /**
     * @param updateCnts Update counts for batch.
     * @param errCode Error code.
     * @param errMsg Error message.
     */
    JdbcBatchExecuteResult(int [] updateCnts, int errCode, String errMsg) {
        super(BATCH_EXEC);

        this.updateCnts = updateCnts;
        this.errCode = errCode;
        this.errMsg = errMsg;
    }

    /**
     * @param type Result type.
     * @param res Result.
     */
    JdbcBatchExecuteResult(byte type, JdbcBatchExecuteResult res) {
        super(type);

        this.updateCnts = res.updateCnts;
        this.errCode = res.errCode;
        this.errMsg = res.errMsg;
    }

    /**
     * @return Update count for DML queries.
     */
    public int[] updateCounts() {
        return updateCnts;
    }

    /**
     * @return Batch error code.
     */
    public int errorCode() {
        return errCode;
    }

    /**
     * @return Batch error message.
     */
    public String errorMessage() {
        return errMsg;
    }

    /** {@inheritDoc} */
    @Override public void writeBinary(BinaryWriterExImpl writer,
        ClientListenerProtocolVersion ver) throws BinaryObjectException {
        super.writeBinary(writer, ver);

        writer.writeInt(errCode);
        writer.writeString(errMsg);
        writer.writeIntArray(updateCnts);
    }


    /** {@inheritDoc} */
    @Override public void readBinary(BinaryReaderExImpl reader,
        ClientListenerProtocolVersion ver) throws BinaryObjectException {
        super.readBinary(reader, ver);

        errCode = reader.readInt();
        errMsg = reader.readString();
        updateCnts = reader.readIntArray();
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(JdbcBatchExecuteResult.class, this, super.toString());
    }
}
