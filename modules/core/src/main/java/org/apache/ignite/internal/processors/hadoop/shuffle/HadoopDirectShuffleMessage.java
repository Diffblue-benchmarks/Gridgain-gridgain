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

package org.apache.ignite.internal.processors.hadoop.shuffle;

import org.apache.ignite.internal.GridDirectTransient;
import org.apache.ignite.internal.IgniteCodeGeneratingFail;
import org.apache.ignite.internal.processors.hadoop.HadoopJobId;
import org.apache.ignite.internal.processors.hadoop.message.HadoopMessage;
import org.apache.ignite.internal.util.tostring.GridToStringInclude;
import org.apache.ignite.internal.util.typedef.internal.S;
import org.apache.ignite.internal.util.typedef.internal.U;
import org.apache.ignite.plugin.extensions.communication.Message;
import org.apache.ignite.plugin.extensions.communication.MessageReader;
import org.apache.ignite.plugin.extensions.communication.MessageWriter;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.nio.ByteBuffer;

/**
 * Direct shuffle message.
 */
@IgniteCodeGeneratingFail
public class HadoopDirectShuffleMessage implements Message, HadoopMessage {
    /** */
    private static final long serialVersionUID = 0L;

    /** */
    @GridToStringInclude
    private HadoopJobId jobId;

    /** */
    @GridToStringInclude
    private int reducer;

    /** Count. */
    private int cnt;

    /** Buffer. */
    private byte[] buf;

    /** Buffer length (equal or less than buf.length). */
    @GridDirectTransient
    private transient int bufLen;

    /** Data length. */
    private int dataLen;

    /**
     * Default constructor.
     */
    public HadoopDirectShuffleMessage() {
        // No-op.
    }

    /**
     * Constructor.
     *
     * @param jobId Job ID.
     * @param reducer Reducer.
     * @param cnt Count.
     * @param buf Buffer.
     * @param bufLen Buffer length.
     * @param dataLen Data length.
     */
    public HadoopDirectShuffleMessage(HadoopJobId jobId, int reducer, int cnt, byte[] buf, int bufLen, int dataLen) {
        assert jobId != null;

        this.jobId = jobId;
        this.reducer = reducer;
        this.cnt = cnt;
        this.buf = buf;
        this.bufLen = bufLen;
        this.dataLen = dataLen;
    }

    /**
     * @return Job ID.
     */
    public HadoopJobId jobId() {
        return jobId;
    }

    /**
     * @return Reducer.
     */
    public int reducer() {
        return reducer;
    }

    /**
     * @return Count.
     */
    public int count() {
        return cnt;
    }

    /**
     * @return Buffer.
     */
    public byte[] buffer() {
        return buf;
    }

    /**
     * @return Data length.
     */
    public int dataLength() {
        return dataLen;
    }

    /** {@inheritDoc} */
    @Override public boolean writeTo(ByteBuffer buf, MessageWriter writer) {
        writer.setBuffer(buf);

        if (!writer.isHeaderWritten()) {
            if (!writer.writeHeader(directType(), fieldsCount()))
                return false;

            writer.onHeaderWritten();
        }

        switch (writer.state()) {
            case 0:
                if (!writer.writeByteArray("buf", this.buf))
                    return false;

                writer.incrementState();

            case 1:
                if (!writer.writeInt("cnt", cnt))
                    return false;

                writer.incrementState();

            case 2:
                if (!writer.writeInt("dataLen", dataLen))
                    return false;

                writer.incrementState();

            case 3:
                if (!writer.writeMessage("jobId", jobId))
                    return false;

                writer.incrementState();

            case 4:
                if (!writer.writeInt("reducer", reducer))
                    return false;

                writer.incrementState();

        }

        return true;
    }

    /** {@inheritDoc} */
    @Override public boolean readFrom(ByteBuffer buf, MessageReader reader) {
        reader.setBuffer(buf);

        if (!reader.beforeMessageRead())
            return false;

        switch (reader.state()) {
            case 0:
                this.buf = reader.readByteArray("buf");

                if (!reader.isLastRead())
                    return false;

                reader.incrementState();

            case 1:
                cnt = reader.readInt("cnt");

                if (!reader.isLastRead())
                    return false;

                reader.incrementState();

            case 2:
                dataLen = reader.readInt("dataLen");

                if (!reader.isLastRead())
                    return false;

                reader.incrementState();

            case 3:
                jobId = reader.readMessage("jobId");

                if (!reader.isLastRead())
                    return false;

                reader.incrementState();

            case 4:
                reducer = reader.readInt("reducer");

                if (!reader.isLastRead())
                    return false;

                reader.incrementState();

        }

        return reader.afterMessageRead(HadoopDirectShuffleMessage.class);
    }

    /** {@inheritDoc} */
    @Override public short directType() {
        return -42;
    }

    /** {@inheritDoc} */
    @Override public byte fieldsCount() {
        return 5;
    }

    /** {@inheritDoc} */
    @Override public void onAckReceived() {
        // No-op.
    }

    /** {@inheritDoc} */
    @Override public void writeExternal(ObjectOutput out) throws IOException {
        jobId.writeExternal(out);

        out.writeInt(reducer);
        out.writeInt(cnt);

        U.writeByteArray(out, buf);

        out.writeInt(dataLen);
    }

    /** {@inheritDoc} */
    @Override public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        jobId = new HadoopJobId();
        jobId.readExternal(in);

        reducer = in.readInt();
        cnt = in.readInt();

        buf = U.readByteArray(in);
        bufLen = buf != null ? buf.length : 0;

        dataLen = in.readInt();
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(HadoopDirectShuffleMessage.class, this);
    }
}
