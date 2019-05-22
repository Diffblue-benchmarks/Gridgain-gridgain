/*
 * Copyright 2004-2019 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (http://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.test.synth;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import org.h2.util.StringUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * A test that runs random join statements against two databases and compares the results.
 */
public class TestHashJoin {
    private static final int LEFT_CNT = 1000;
    private static final int RIGHT_CNT = 100;

    private static Connection connection;

    /**
     * @throws SQLException On error.
     */
    @BeforeClass
    public static void init() throws SQLException {
        connection = DriverManager.getConnection("jdbc:h2:mem:hashjoin");

        sqlStr("SET HASH_JOIN_ENABLE 1");

        sqlStr("DROP TABLE IF EXISTS A");
        sqlStr("DROP TABLE IF EXISTS B");

        sqlStr("CREATE TABLE A (ID INT PRIMARY KEY, JID INT)");

        for (int i = 0; i < LEFT_CNT; ++i)
            sqlStr("INSERT INTO A VALUES(?, ?)", i, i % 3 == 0 ? null : i);

        sqlStr("CREATE TABLE B(ID INT PRIMARY KEY, val0 int, val1 VARCHAR(20), A_JID INT, val2 BOOLEAN)");
        sqlStr("CREATE INDEX B_A_JID ON B(A_JID)");
        sqlStr("CREATE INDEX B_VAL0 ON B(VAL0)");

        sqlStr("CREATE TABLE C(ID INT PRIMARY KEY, val0 int, val1 VARCHAR(20), A_JID INT, val2 BOOLEAN)");
        sqlStr("CREATE INDEX C_A_JID ON C(A_JID)");
        sqlStr("CREATE INDEX C_VAL0 ON C(VAL0)");


        for (int i = 0; i < RIGHT_CNT; ++i)
            sqlStr("INSERT INTO B (ID, A_JID, val0) VALUES(?, ?, ?)",
                i,
                i % 4 == 0 ? null : i,
                i == 0 ? null : i % 10);

        sqlStr("INSERT INTO B (ID, A_JID, val0, val1, val2) VALUES(?, ?, ?, ?, ?)",
            RIGHT_CNT,
            RIGHT_CNT % 4,
            null, null, null);

        for (int i = 0; i < RIGHT_CNT; ++i)
            sqlStr("INSERT INTO C (ID, A_JID, val0) VALUES(?, ?, ?)",
                i,
                i % 4 == 0 ? null : i,
                i == 0 ? null : i % 10);
    }

    /**
     * @throws SQLException On error.
     */
    @AfterClass
    public static void cleanup() throws SQLException {
        sqlStr("DROP TABLE IF EXISTS A");
        sqlStr("DROP TABLE IF EXISTS B");

        connection.close();
    }

    /**
     * Check query plan. HASH_JOIN index must be chosen.
     * @throws Exception On error.
     */
    @Test
    public void testHashJoin() throws Exception {
        String plan = sqlStr("EXPLAIN SELECT * FROM A, B USE INDEX (HASH_JOIN) WHERE A.JID=B.A_JID");
        assertTrue(plan.contains("HASH_JOIN [fillFromIndex=B_DATA, hashedCols=[A_JID]]"));

        plan = sqlStr("EXPLAIN SELECT * FROM A, B USE INDEX (HASH_JOIN), C USE INDEX (HASH_JOIN) " +
            "WHERE A.JID=B.A_JID AND A.JID=C.A_JID");
        assertTrue(plan.contains("HASH_JOIN [fillFromIndex=B_DATA, hashedCols=[A_JID]]"));
        assertTrue(plan.contains("HASH_JOIN [fillFromIndex=C_DATA, hashedCols=[A_JID]]"));

        plan = sqlStr("EXPLAIN SELECT * FROM A, B USE INDEX (HASH_JOIN), C " +
            "WHERE A.JID=B.A_JID AND A.JID=C.A_JID");
        assertTrue(plan.contains("PUBLIC.C_A_JID: A_JID = A.JID"));
        assertTrue(plan.contains("HASH_JOIN [fillFromIndex=B_DATA, hashedCols=[A_JID]]"));

        plan = sqlStr("EXPLAIN SELECT * FROM A, B, C USE INDEX (HASH_JOIN) " +
            "WHERE A.JID=B.A_JID AND A.JID=C.A_JID");
        assertTrue(plan.contains("PUBLIC.B_A_JID: A_JID = A.JID"));
        assertTrue(plan.contains("HASH_JOIN [fillFromIndex=C_DATA, hashedCols=[A_JID]]"));

        plan = sqlStr("EXPLAIN SELECT * FROM A, B USE INDEX (HASH_JOIN), C USE INDEX (HASH_JOIN) " +
            "WHERE A.JID=B.A_JID AND B.ID=C.ID");
        assertTrue(plan.contains("HASH_JOIN [fillFromIndex=B_DATA, hashedCols=[A_JID]"));
        assertTrue(plan.contains("HASH_JOIN [fillFromIndex=C_DATA, hashedCols=[ID]]"));


        plan = sqlStr("EXPLAIN SELECT * FROM A, B USE INDEX (HASH_JOIN), C " +
            "WHERE A.JID=B.A_JID AND B.VAL0=C.VAL0");
        assertTrue(plan.contains("HASH_JOIN [fillFromIndex=B_DATA, hashedCols=[A_JID]"));
        assertTrue(plan.contains("PUBLIC.C_VAL0: VAL0 = B.VAL0"));

        plan = sqlStr("EXPLAIN SELECT * FROM A, B, C USE INDEX (HASH_JOIN) " +
            "WHERE A.JID=B.A_JID AND B.VAL0=C.VAL0");
        assertTrue(plan.contains("PUBLIC.B_A_JID: A_JID = A.JID"));
        assertTrue(plan.contains("HASH_JOIN [fillFromIndex=C_DATA, hashedCols=[VAL0]]"));
    }

    /**
     * @throws Exception On error.
     */
    @Test
    public void testHashJoinFilterCondition() throws Exception {
        assertTrue(sqlStr("EXPLAIN SELECT * FROM A, B USE INDEX (HASH_JOIN) " +
            "WHERE A.JID = B.A_JID AND B.val0 > ?", 5)
            .contains("HASH_JOIN [fillFromIndex=B_VAL0, hashedCols=[A_JID], filters=[VAL0 > ?1]]"));
    }

    /**
     * @throws Exception On error.
     */
    @Test
    public void testNotHashJoin() throws Exception {
        assertFalse(sqlStr("EXPLAIN SELECT * FROM A where A.JID IN (NULL, NULL)").contains("HASH_JOIN"));
        assertFalse(sqlStr("EXPLAIN SELECT * FROM A, B WHERE A.JID > B.A_JID").contains("HASH_JOIN"));
        assertFalse(sqlStr("EXPLAIN SELECT * FROM A, B WHERE A.JID > B.A_JID AND B.A_JID = ?", 5)
            .contains("HASH_JOIN"));
    }

    /**
     * @param sql SQL query.
     * @param params Parameters.
     * @throws SQLException On error.
     * @return Result set or updated count are printed to string.
     */
    private static String sqlStr(String sql, Object... params) throws SQLException {
        try (PreparedStatement prep = connection.prepareStatement(sql)) {
            for (int j = 0; j < params.length; j++)
                prep.setObject(j + 1, params[j]);

            if (prep.execute()) {
                ResultSet rs = prep.getResultSet();

                return readResult(rs);
            }
            else
                return "UPD: " + prep.getUpdateCount();
        }
    }

    /**
     * @param rs Result set.
     * @return Result set printed to string.
     * @throws SQLException On error.
     */
    private static String readResult(ResultSet rs) throws SQLException {
        StringBuilder b = new StringBuilder();

        ResultSetMetaData meta = rs.getMetaData();

        int columnCount = meta.getColumnCount();

        for (int i = 0; i < columnCount; i++) {
            if (i > 0)
                b.append(",");

            b.append(StringUtils.toUpperEnglish(meta.getColumnLabel(i + 1)));
        }

        b.append(":\n");

        String result = b.toString();

        ArrayList<String> list = new ArrayList<>();

        while (rs.next()) {
            b = new StringBuilder();

            for (int i = 0; i < columnCount; i++) {
                if (i > 0)
                    b.append(",");

                b.append(rs.getString(i + 1));
            }

            list.add(b.toString());
        }
        Collections.sort(list);

        for (int i = 0; i < list.size(); i++)
            result += list.get(i) + "\n";

        return result;
    }
}
