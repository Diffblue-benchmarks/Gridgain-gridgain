/*
 * Copyright 2004-2019 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (http://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.index;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.h2.command.dml.AllColumnsForPlan;
import org.h2.engine.Session;
import org.h2.message.DbException;
import org.h2.message.Trace;
import org.h2.result.Row;
import org.h2.result.SearchRow;
import org.h2.result.SortOrder;
import org.h2.table.Column;
import org.h2.table.IndexColumn;
import org.h2.table.Table;
import org.h2.table.TableFilter;
import org.h2.value.Value;
import org.h2.value.ValueStringIgnoreCase;

/**
 * An unique index based on an in-memory hash map.
 */
public class HashJoinIndex extends BaseIndex {
    /**
     * Hash table by column specified by colId.
     */
    private Map<Value, List<Row>> hashTbl;

    private int colId = -1;

    private final IteratorCursor cur = new IteratorCursor();

    private boolean ignorecase;

    /**
     * @param table Table to build temporary hash join index.
     */
    public HashJoinIndex(Table table) {
        super(table, 0, "HASH_JOIN",
            IndexColumn.wrap(table.getColumns()), IndexType.createUnique(false, true));
    }

    @Override
    public void add(Session session, Row row) {
        // nothing to do
    }

    @Override
    public void remove(Session session, Row row) {
        // nothing to do
    }

    @Override
    public long getRowCount(Session session) {

        return getRowCountApproximation();
    }

    @Override
    public long getRowCountApproximation() {
        return 0;
    }

    @Override
    public long getDiskSpaceUsed() {
        return 0;
    }

    @Override
    public void close(Session session) {
        // nothing to do
    }

    @Override
    public void remove(Session session) {
        // nothing to do
    }

    /** {@inheritDoc} */
    @Override public void truncate(Session session) {
        // nothing to do
    }

    @Override
    public double getCost(Session session, int[] masks,
            TableFilter[] filters, int filter, SortOrder sortOrder,
            AllColumnsForPlan allColumnsSet) {

       for (Column column : columns) {
            int index = column.getColumnId();
            int mask = masks[index];
            if (mask != 0 && ((mask & IndexCondition.EQUALITY) == IndexCondition.EQUALITY))
                return 2 + table.getRowCount(session);
        }

        return Long.MAX_VALUE;
    }

    @Override
    public void checkRename() {
        // ok
    }

    @Override
    public boolean needRebuild() {
        return true;
    }

    @Override
    public boolean canGetFirstOrLast() {
        return false;
    }

    @Override
    public Cursor findFirstOrLast(Session session, boolean first) {
        throw DbException.getUnsupportedException("HASH");
    }

    @Override
    public boolean canScan() {
        return false;
    }

    @Override public Cursor find(Session session, SearchRow first, SearchRow last) {
        assert first.getValue(colId).equals(last.getValue(colId)) : "HASH f=" + first + ", l=" + last;

        Value key = first.getValue(colId);

        if (ignorecase)
            key = ValueStringIgnoreCase.get(key.getString());

        List<Row> res = hashTbl.get(key);

        if (res == null)
            res = Collections.emptyList();

        cur.open(res.iterator());

        return cur;
    }

    /**
     * @param ses Session.
     * @param masks Columns index mask.
     */
    public void build(Session ses, int[] masks) {
        long t0 = System.currentTimeMillis();

        for (int i = 0; i < masks.length; ++i) {
            assert masks[i] == 0 || masks[i] == IndexCondition.EQUALITY;

            if (masks[i] != 0)
                colId = i;
        }

        ignorecase = table.getColumn(colId).getType().getValueType() == Value.STRING
            && ses.getDatabase().getIgnoreCase()
            || table.getColumn(colId).getType().getValueType() == Value.STRING_IGNORECASE;

        Index idx = table.getScanIndex(ses);

        Cursor cur = idx.find(ses, null, null);

        hashTbl = new HashMap<>();

        while(cur.next()) {
            Row r = cur.get();

            Value key = r.getValue(colId);

            if (ignorecase)
                key = ValueStringIgnoreCase.get(key.getString());

            List<Row> keyRows = hashTbl.get(key);

            if (keyRows == null) {
                keyRows = new ArrayList<>();

                hashTbl.put(key, keyRows);
            }

            keyRows.add(r);
        }

        Trace t = ses.getTrace();

        if (t.isDebugEnabled())
            t.debug("Build hash table for {0}: {1} ms", table.getName(), System.currentTimeMillis() - t0);
    }

    /**
     * @param masks Index masks.
     * @return true if Hash JOIN index is applicable for specifid masks: there is EQUALITY for only one column.
     */
    public static boolean isApplicable(Session ses, Table tbl, int[] masks) {
        if (masks == null || !tbl.canGetRowCount())
            return false;

        if (tbl.getRowCount(ses) > 100_000)
            return false;

        boolean applicable = false;

        for (int i = 0; i < masks.length; ++i) {
            if (masks[i] == IndexCondition.EQUALITY)
                if (!applicable)
                    applicable = true;
                else
                    return false;
        }

        return applicable;
    }

    /**
     *
     */
    private static class IteratorCursor implements Cursor {
        private Iterator<Row> it;
        private Row current;

        /**
         * @param it Iterator.
         */
        public void open(Iterator<Row> it) {
            this.it = it;
            current = null;
        }


        /** {@inheritDoc} */
        @Override public boolean previous() {
            throw DbException.getUnsupportedException("prev");
        }

        /** {@inheritDoc} */
        @Override public boolean next() {
            if (it.hasNext()) {
                current = it.next();
                return true;
            }
            current = null;
            return false;
        }

        /** {@inheritDoc} */
        @Override public Row getSearchRow() {
            return get();
        }

        /** {@inheritDoc} */
        @Override public Row get() {
            return current;
        }

        /** {@inheritDoc} */
        @Override public String toString() {
            return "IteratorCursor->" + current;
        }
    }
}
