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

package org.apache.ignite.cache.store.jdbc;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.UUID;

/**
 * Default implementation of {@link JdbcTypesTransformer}.
 */
public class JdbcTypesDefaultTransformer implements JdbcTypesTransformer {
    /** */
    private static final long serialVersionUID = 0L;

    /** Singleton instance to use. */
    public static final JdbcTypesDefaultTransformer INSTANCE = new JdbcTypesDefaultTransformer();

    /** {@inheritDoc} */
    @Override public Object getColumnValue(ResultSet rs, int colIdx, Class<?> type) throws SQLException {
        if (type == String.class)
            return rs.getString(colIdx);

        if (type == int.class || type == Integer.class) {
            int res = rs.getInt(colIdx);

            return rs.wasNull() && type == Integer.class ? null : res;
        }

        if (type == long.class || type == Long.class) {
            long res = rs.getLong(colIdx);

            return rs.wasNull() && type == Long.class ? null : res;
        }

        if (type == double.class || type == Double.class) {
            double res = rs.getDouble(colIdx);

            return rs.wasNull() && type == Double.class ? null : res;
        }

        if (type == Date.class || type == java.util.Date.class)
            return rs.getDate(colIdx);

        if (type == Timestamp.class)
            return rs.getTimestamp(colIdx);

        if (type == Time.class)
            return rs.getTime(colIdx);

        if (type == boolean.class || type == Boolean.class) {
            boolean res = rs.getBoolean(colIdx);

            return rs.wasNull() && type == Boolean.class ? null : res;
        }

        if (type == byte.class || type == Byte.class) {
            byte res = rs.getByte(colIdx);

            return rs.wasNull() && type == Byte.class ? null : res;
        }

        if (type == short.class || type == Short.class) {
            short res = rs.getShort(colIdx);

            return rs.wasNull() && type == Short.class ? null : res;
        }

        if (type == float.class || type == Float.class) {
            float res = rs.getFloat(colIdx);

            return rs.wasNull() && type == Float.class ? null : res;
        }

        if (type == BigDecimal.class)
            return rs.getBigDecimal(colIdx);

        if (type == UUID.class) {
            Object res = rs.getObject(colIdx);

            if (res instanceof UUID)
                return res;

            if (res instanceof byte[]) {
                ByteBuffer bb = ByteBuffer.wrap((byte[])res);

                long most = bb.getLong();
                long least = bb.getLong();

                return new UUID(most, least);
            }

            if (res instanceof String)
                return UUID.fromString((String)res);
        }

        if (type.isEnum()) {
            if (NUMERIC_TYPES.contains(rs.getMetaData().getColumnType(colIdx))) {
                int ordinal = rs.getInt(colIdx);

                Object[] values = type.getEnumConstants();

                return rs.wasNull() || ordinal >= values.length ? null : values[ordinal];
            }

            String str = rs.getString(colIdx);

            try {
                return rs.wasNull() ? null : Enum.valueOf((Class<? extends Enum>) type, str.trim());
            }
            catch (IllegalArgumentException ignore) {
                return null;
            }
        }

        return rs.getObject(colIdx);
    }
}
