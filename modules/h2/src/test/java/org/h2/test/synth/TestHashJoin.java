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
import java.sql.SQLException;
import org.h2.engine.Session;
import org.h2.jdbc.JdbcConnection;
import org.h2.test.TestBase;
import org.h2.test.TestDb;

/**
 * A test that runs random join statements against two databases and compares the results.
 */
public class TestHashJoin extends TestDb {
    private static final int LEFT_CNT = 10000;
    private static final int RIGHT_CNT = 10;

    private Connection connection;

    /**
     * Run just this test.
     *
     * @param a ignored
     */
    public static void main(String... a) throws Exception {
        TestBase.createCaller().init().test();
    }

    @Override
    public void test() throws Exception {
//        testHashJoin();
        testNotHashJoin();
    }

    private void testHashJoin() throws Exception {
//        while (true) {
        connection = DriverManager.getConnection("jdbc:h2:mem:hashjoin");

        ((Session)((JdbcConnection)connection).getSession()).setHashJoinEnabled(true);

        sql("DROP TABLE IF EXISTS A");
        sql("DROP TABLE IF EXISTS B");

        sql("CREATE TABLE A (ID INT PRIMARY KEY, JID INT)");

        for (int i = 0; i < LEFT_CNT; ++i)
            sql("INSERT INTO A VALUES(?, ?)", i, i % 3 == 0 ? null : i);

        sql("CREATE TABLE B(ID INT PRIMARY KEY, val0 int, val1 int, A_JID INT, val3 int)");
        sql("CREATE INDEX B_A_JID ON B(A_JID)");

        for (int i = 0; i < RIGHT_CNT; ++i)
            sql("INSERT INTO B (ID, A_JID) VALUES(?, ?)", i, i % 4 == 0 ? null : i);

        sql("SET TRACE_LEVEL_SYSTEM_OUT 10");

        long t0 = System.currentTimeMillis();

        for (int i = 0; i < 1000; ++i)
//                sql("SELECT * FROM A JOIN B ON A.JID=B.A_JID");
            sql("SELECT * FROM A, B WHERE A.JID=B.A_JID");
//                sql("SELECT * FROM A, B USE INDEX (HASH_JOIN) WHERE A.JID=B.A_JID");

        System.out.println(System.currentTimeMillis() - t0);
        sql("DROP TABLE IF EXISTS A");
        sql("DROP TABLE IF EXISTS B");
//        }
    }

    private void testNotHashJoin() throws Exception {
//        while (true) {
        connection = DriverManager.getConnection("jdbc:h2:mem:nothashjoin");

        ((Session)((JdbcConnection)connection).getSession()).setHashJoinEnabled(true);

        sql("DROP TABLE IF EXISTS A");

        sql("CREATE TABLE A (ID INT PRIMARY KEY, JID INT)");

        for (int i = 0; i < RIGHT_CNT; ++i)
            sql("INSERT INTO A VALUES(?, ?)", i, i % 3 == 0 ? null : i);

        sql("SET TRACE_LEVEL_SYSTEM_OUT 10");

        sql("SELECT * FROM A where A.JID IN (NULL, NULL)");
        sql("DROP TABLE IF EXISTS A");
//        }
    }

    private void sql(String sql, Object... params) throws SQLException {
        try (PreparedStatement prep = connection.prepareStatement(sql)) {
            for (int j = 0; j < params.length; j++)
                prep.setObject(j + 1, params[j]);

            if (prep.execute()) {
                ResultSet rs = prep.getResultSet();
                int columnCount = rs.getMetaData().getColumnCount();

                while (rs.next()) {
//                    for (int i = 0; i < columnCount; ++i)
//                        System.out.print(rs.getObject(i + 1)+ " ");
//
//                    System.out.println();
                }
            }
        }
    }
}
