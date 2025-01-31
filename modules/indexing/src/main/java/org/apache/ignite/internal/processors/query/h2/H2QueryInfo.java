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

package org.apache.ignite.internal.processors.query.h2;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.apache.ignite.IgniteLogger;
import org.apache.ignite.internal.processors.cache.query.IgniteQueryErrorCode;
import org.apache.ignite.internal.processors.query.IgniteSQLException;
import org.apache.ignite.internal.util.typedef.internal.LT;
import org.apache.ignite.internal.util.typedef.internal.U;
import org.h2.engine.Session;

/**
 * Base H2 query info with commons for MAP, LOCAL, REDUCE queries.
 */
public class H2QueryInfo {
    /** Type. */
    private final QueryType type;

    /** Begin timestamp. */
    private final long beginTs;

    /** Query schema. */
    private final String schema;

    /** Query SQL. */
    private final String sql;

    /** Enforce join order. */
    private final boolean enforceJoinOrder;

    /** Join batch enabled (distributed join). */
    private final boolean distributedJoin;

    /** Lazy mode. */
    private final boolean lazy;

    /**
     * @param type Query type.
     * @param stmt Query statement.
     * @param sql Query statement.
     */
    public H2QueryInfo(QueryType type, PreparedStatement stmt, String sql) {
        try {
            assert stmt != null;

            this.type = type;
            this.sql = sql;

            beginTs = U.currentTimeMillis();

            schema = stmt.getConnection().getSchema();

            Session s = H2Utils.session(stmt.getConnection());

            enforceJoinOrder = s.isForceJoinOrder();
            distributedJoin = s.isJoinBatchEnabled();
            lazy = s.isLazyQueryExecution();
        }
        catch (SQLException e) {
            throw new IgniteSQLException("Cannot collect query info", IgniteQueryErrorCode.UNKNOWN, e);
        }
    }

    /**
     * Print info specified by children.
     *
     * @param msg Message string builder.
     */
    protected void printInfo(StringBuilder msg) {
        // No-op.
    }

    /**
     * @return Query execution time.
     */
    public long time() {
        return U.currentTimeMillis() - beginTs;
    }

    /**
     * @param log Logger.
     * @param msg Log message
     * @param connMgr Connection manager.
     */
    public void printLogMessage(IgniteLogger log, ConnectionManager connMgr, String msg) {
        StringBuilder msgSb = new StringBuilder(msg + " [");

        msgSb.append("time=").append(time()).append("ms")
            .append(", type=").append(type)
            .append(", distributedJoin=").append(distributedJoin)
            .append(", enforceJoinOrder=").append(enforceJoinOrder)
            .append(", lazy=").append(lazy);

        printInfo(msgSb);

        msgSb.append(", sql='")
            .append(sql);

        if (type != QueryType.REDUCE)
            msgSb.append("', plan=").append(queryPlan(log, connMgr));

        msgSb.append(']');

        LT.warn(log, msgSb.toString());
    }

    /**
     * @param log Logger.
     * @param connMgr Connection manager.
     * @return Query plan.
     */
    protected String queryPlan(IgniteLogger log, ConnectionManager connMgr) {
        Connection c = connMgr.connectionForThread().connection(schema);

        H2Utils.setupConnection(c, null, distributedJoin, enforceJoinOrder);

        try (PreparedStatement pstmt = c.prepareStatement("EXPLAIN " + sql)) {

            try (ResultSet plan = pstmt.executeQuery()) {
                plan.next();

                return plan.getString(1) + U.nl();
            }
        }
        catch (Exception e) {
            log.warning("Cannot get plan for long query: " + sql, e);

            return "[error on calculate plan: " + e.getMessage() + ']';
        }
    }

    /**
     * Query type.
     */
    public enum QueryType {
        LOCAL,
        MAP,
        REDUCE
    }
}
