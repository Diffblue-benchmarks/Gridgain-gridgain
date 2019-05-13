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

package org.h2.index;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.h2.command.dml.AllColumnsForPlan;
import org.h2.engine.DbObject;
import org.h2.engine.Session;
import org.h2.expression.ExpressionVisitor;
import org.h2.expression.condition.Comparison;
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
import org.h2.value.ValueArray;
import org.h2.value.ValueStringIgnoreCase;

/**
 * An unique index based on an in-memory hash map.
 */
public class HashJoinIndex extends BaseIndex {
    /**
     * Hash table by column specified by colId.
     */
    private Map<Value, List<Row>> hashTbl;

    /** Key columns to build hash table. */
    private int [] colIds;

    /** Ignorecase flags for each column of EQUI join. */
    private boolean [] ignorecase;

    /** Cursor. */
    private final IteratorCursor cur = new IteratorCursor();

    /**
     * @param table Table to build temporary hash join index.
     */
    public HashJoinIndex(Table table) {
        super(table, 0, "HASH_JOIN",
            IndexColumn.wrap(table.getColumns()), IndexType.createUnique(false, true));
    }

    /** {@inheritDoc} */
    @Override public void add(Session session, Row row) {
        throw new UnsupportedOperationException("Runtime HASH_JOIN index doesn't support 'add'");
    }

    /** {@inheritDoc} */
    @Override public void remove(Session session, Row row) {
        throw new UnsupportedOperationException("Runtime HASH_JOIN index doesn't support 'remove'");
    }

    /** {@inheritDoc} */
    @Override public long getRowCount(Session session) {
        return getRowCountApproximation();
    }

    /** {@inheritDoc} */
    @Override public long getRowCountApproximation() {
        return 0;
    }

    /** {@inheritDoc} */
    @Override public long getDiskSpaceUsed() {
        return 0;
    }

    /** {@inheritDoc} */
    @Override public void close(Session session) {
        hashTbl = null;
    }

    /** {@inheritDoc} */
    @Override public void remove(Session session) {
        // No-op.
    }

    /** {@inheritDoc} */
    @Override public void truncate(Session session) {
        // No-op.
    }

    /** {@inheritDoc} */
    @Override public double getCost(Session session, int[] masks,
            TableFilter[] filters, int filter, SortOrder sortOrder,
            AllColumnsForPlan allColumnsSet) {

       for (Column column : columns) {
            int index = column.getColumnId();

            int mask = masks[index];

            if (mask != 0 && ((mask & IndexCondition.EQUALITY) == IndexCondition.EQUALITY))
                return 2 + table.getRowCountApproximation();
        }

        return Long.MAX_VALUE;
    }

    /** {@inheritDoc} */
    @Override public void checkRename() {
        // No-op.
    }

    /** {@inheritDoc} */
    @Override public boolean needRebuild() {
        return false;
    }

    /** {@inheritDoc} */
    @Override public boolean canGetFirstOrLast() {
        return false;
    }

    /** {@inheritDoc} */
    @Override public Cursor findFirstOrLast(Session session, boolean first) {
        throw DbException.getUnsupportedException("HASH");
    }

    /** {@inheritDoc} */
    @Override public boolean canScan() {
        return false;
    }

    /** {@inheritDoc} */
    @Override public Cursor find(Session session, SearchRow first, SearchRow last) {
        Value key = hashKey(first);

        List<Row> res = hashTbl.get(key);

        if (res == null)
            res = Collections.emptyList();

        cur.open(res.iterator());

        return cur;
    }

    /**
     * @return {@code true} if the hash table has been built already.
     */
    public boolean isBuilt() {
        return hashTbl != null;
    }

    /**
     * @param ses Session.
     * @param masks Columns index mask.
     * @param indexConditions Index conditions to filter values when hash table is built.
     */
    public void build(Session ses, int[] masks, ArrayList<IndexCondition> indexConditions) {
        long t0 = System.currentTimeMillis();

        List<Integer> ids = new ArrayList<>();
        for (int i = 0; i < masks.length; ++i) {
            if (masks[i] == IndexCondition.EQUALITY)
                ids.add(i);
        }

        colIds = new int[ids.size()];
        ignorecase = new boolean[ids.size()];

        for (int i = 0; i < colIds.length; ++i) {
            colIds[i] = ids.get(i);

            ignorecase[i] |= table.getColumn(colIds[i]).getType().getValueType() == Value.STRING
                && ses.getDatabase().getIgnoreCase()
                || table.getColumn(colIds[i]).getType().getValueType() == Value.STRING_IGNORECASE;
        }

        Index idx = table.getScanIndex(ses);

        Cursor cur = idx.find(ses, null, null);

        hashTbl = new HashMap<>();

        while(cur.next()) {
            Row r = cur.get();

            Value key = hashKey(r);

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
     * @param r Row.
     * @return Hash key.
     */
    private Value hashKey(SearchRow r) {
        if (colIds.length == 1)
            return ignorecaseIfNeed(r.getValue(colIds[0]), ignorecase[0]);

        Value [] key = new Value[colIds.length];

        for (int i = 0; i < colIds.length; ++i)
            key[i] =  ignorecaseIfNeed(r.getValue(colIds[i]), ignorecase[i]);

        return ValueArray.get(key);
    }

    /**
     * @param key Key value.
     * @param ignorecase Flag to ignorecase.
     * @return Ignorecase wrapper Value.
     */
    private static Value ignorecaseIfNeed(Value key, boolean ignorecase) {
        return ignorecase ? ValueStringIgnoreCase.get(key.getString()) : key;
    }

    /**
     * @param condition Index condition to test.
     * @return {@code true} if the filter is used in a EQUI-JOIN.
     */
    public static boolean isEquiJoinCondition(IndexCondition condition) {
        HashSet<DbObject> dependencies = new HashSet<>();

        ExpressionVisitor depsVisitor = ExpressionVisitor.getDependenciesVisitor(dependencies);

        if (condition.getExpression() == null)
            return false;

        condition.getExpression().isEverything(depsVisitor);

        int cmpType = condition.getCompareType();

        return dependencies.size() == 1 && (cmpType == Comparison.EQUAL || cmpType == Comparison.EQUAL_NULL_SAFE);
    }


    /**
     * @param ses Session.
     * @param tbl Source table to build hash map.
     * @param masks Index masks.
     * @return true if Hash JOIN index is applicable for specifid masks: there is EQUALITY for only one column.
     */
    public static boolean isApplicable(Session ses, Table tbl, int[] masks) {
        if (masks == null || tbl.getRowCountApproximation() > 100_000)
            return false;

        return true;
    }

    /**
     *
     */
    private static class IteratorCursor implements Cursor {
        /** Iterator. */
        private Iterator<Row> it;

        /** Current row. */
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
