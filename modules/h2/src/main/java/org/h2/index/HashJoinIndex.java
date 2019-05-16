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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import org.h2.table.IndexHints;
import org.h2.table.Table;
import org.h2.table.TableFilter;
import org.h2.value.Value;
import org.h2.value.ValueArray;
import org.h2.value.ValueStringIgnoreCase;

/**
 * An unique index based on an in-memory hash map.
 */
public class HashJoinIndex extends BaseIndex {
    /** String constant for Hash join hint, index name etc.. */
    public static final String HASH_JOIN = "HASH_JOIN";

    /** Cursor. */
    private final IteratorCursor cur = new IteratorCursor();

    /** Hash table by column specified by colId. */
    private Map<Value, List<Row>> hashTbl;

    /** Key columns to build hash table. */
    private int[] colIds;

    /** Ignorecase flags for each column of EQUI join. */
    private boolean[] ignorecase;

    /** Index to fill hash table. */
    private Index fillFromIndex;

    /** The first row to open cursor on #fillFromIndex. */
    private SearchRow first;

    /** The last row to open cursor on #fillFromIndex. */
    private SearchRow last ;

    /** Conditions. */
    private Set<ConditionChecker> condsCheckers;

    /**
     * @param table Table to build temporary hash join index.
     */
    public HashJoinIndex(Table table) {
        super(table, 0, HASH_JOIN,
            IndexColumn.wrap(table.getColumns()), IndexType.createUnique(false, true));
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
     * @param indexHints Index hints to check HASH JOIN enabled.
     * @return true if Hash JOIN index is applicable for specifid masks: there is EQUALITY for only one column.
     */
    public static boolean isApplicable(Session ses, Table tbl, int[] masks, IndexHints indexHints) {
        // TODO: add check the table's size
        return indexHints != null
            && indexHints.getAllowedIndexes() != null
            && indexHints.getAllowedIndexes().size() == 1
            && indexHints.getAllowedIndexes().contains(HASH_JOIN);
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
    @Override public void close(Session ses) {
        // No-op.
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

        double cost = 0;

        for (Column column : columns) {
            int index = column.getColumnId();

            int mask = masks[index];

            if (mask != 0 && ((mask & IndexCondition.EQUALITY) == IndexCondition.EQUALITY))
                if (cost > 0)
                    cost -= 2;
                else
                    cost = 2 + table.getRowCountApproximation();
        }

        return cost > 0 ? cost : Long.MAX_VALUE;
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
        if (hashTbl == null)
            build(session);

        Value key = hashKey(first);

        List<Row> res = hashTbl.get(key);

        if (res == null)
            return Cursor.EMPTY;

        cur.open(res.iterator());

        return cur;
    }

    /** {@inheritDoc} */
    @Override public String getPlanSQL() {
        // Used by debug print before end of prepare.
        if (fillFromIndex == null)
            return "HASH_JOIN";

        StringBuilder builder = new StringBuilder("HASH_JOIN ");

        builder.append("[fillFromIndex=").append(fillFromIndex.getName());

        builder.append(", hashedCols=[");
        for (int i = 0; i < colIds.length; ++i) {
            if (i > 0)
                builder.append(", ");

            builder.append(table.getColumn(colIds[i]).getName());
        }
        builder.append("]");

        if (condsCheckers != null && !condsCheckers.isEmpty()) {
            builder.append(", filters=[");

            int cnt = 0;

            for (ConditionChecker c : condsCheckers) {
                if (cnt > 0)
                    builder.append(", ");

                builder.append(c.getSQL());

                cnt++;
            }
            builder.append("]");
        }
        builder.append("]");

        return builder.toString();
    }

    /**
     * @return {@code true} if the hash table has been built already.
     */
    public boolean isBuilt() {
        return hashTbl != null;
    }

    /**
     * @param ses Session.
     * @param indexConditions Index conditions to filter values when hash table is built.
     */
    public void prepare(Session ses, ArrayList<IndexCondition> indexConditions) {
        assert hashTbl == null;

        List<Integer> ids = new ArrayList<>();

        ArrayList<IndexCondition> filterIndexCond = new ArrayList<>();

        for (IndexCondition idxCond : indexConditions) {
            if (isEquiJoinCondition(idxCond))
                ids.add(idxCond.getColumn().getColumnId());
            else
                filterIndexCond.add(idxCond);
        }

        colIds = new int[ids.size()];
        ignorecase = new boolean[ids.size()];

        // Prepare join col IDs and ignorecase flags.
        for (int i = 0; i < colIds.length; ++i) {
            colIds[i] = ids.get(i);

            ignorecase[i] |= table.getColumn(colIds[i]).getType().getValueType() == Value.STRING
                && ses.getDatabase().getIgnoreCase()
                || table.getColumn(colIds[i]).getType().getValueType() == Value.STRING_IGNORECASE;
        }

         prepareFillFromIndex(ses, filterIndexCond);

        // Prepare filter condition.
        for (IndexCondition idxCond : filterIndexCond) {
            ConditionChecker checker = ConditionChecker.create(ses, idxCond);

            if (condsCheckers == null && checker != null)
                condsCheckers = new HashSet<>();

            if (checker != null)
                condsCheckers.add(checker);
        }
    }

    /**
     * @param ses Session.
     * @param indexConditions Index conditions to choose hte best index to fill hash table.
     */
    private void prepareFillFromIndex(Session ses, ArrayList<IndexCondition> indexConditions) {
        fillFromIndex = table.getScanIndex(ses);
        first = null;
        last = null;
    }


    /**
     * @param ses Session.
     */
    private void build(Session ses) {
        long t0 = System.currentTimeMillis();

        if (condsCheckers != null){
            for (ConditionChecker c : condsCheckers)
                c.calculateValue(ses);
        }

        Cursor cur = fillFromIndex.find(ses, first, last);

        hashTbl = new HashMap<>();

        while (cur.next()) {
            Row r = cur.get();

            if (checkConditions(ses, r)) {
                Value key = hashKey(r);

                List<Row> keyRows = hashTbl.get(key);

                if (keyRows == null) {
                    keyRows = new ArrayList<>();

                    hashTbl.put(key, keyRows);
                }

                keyRows.add(r);
            }
        }

        Trace t = ses.getTrace();

        if (t.isDebugEnabled()) {
            t.debug("Build hash table for {0}, size={1}. Duration={2} ms",
                    table.getName(), hashTbl.size(), System.currentTimeMillis() - t0);
        }
    }

    /**
     * @param r Row.
     * @return Hash key.
     */
    private Value hashKey(SearchRow r) {
        if (colIds.length == 1)
            return ignorecaseIfNeed(r.getValue(colIds[0]), ignorecase[0]);

        Value[] key = new Value[colIds.length];

        for (int i = 0; i < colIds.length; ++i)
            key[i] = ignorecaseIfNeed(r.getValue(colIds[i]), ignorecase[i]);

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
     * @param ses Session.
     * @param r Current row.
     * @return {@code true} if the row is OK with all index conditions.
     */
    private boolean checkConditions(Session ses, Row r) {
        if (condsCheckers != null) {
            for (ConditionChecker checker : condsCheckers) {
                if (!checker.check(getTable(), r))
                    return false;
            }
        }

        return true;
    }

    /**
     * @param session Session.
     */
    public void clearHashTable(Session session) {
        hashTbl = null;
    }

    /**
     *
     */
    private class IteratorCursor implements Cursor {
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

    /**
     *
     */
    private abstract static class ConditionChecker {
        /** Column ID. */
        int colId;

        /** Value to compare. */
        Value v;

        /** Value expr. */
        IndexCondition idxCond;

        /**
         * @param tbl Table to compare.
         * @param r Row to check condition.
         * @return {@code true} if row is OK with the condition.
         */
        boolean check(Table tbl, Row r) {
            Value o = r.getValue(colId);

            if (o.containsNull())
                return false;

            return checkValue(tbl, o);
        }

        /**
         * @param ses Session.
         */
        void calculateValue(Session ses) {
            v = idxCond.getCurrentValue(ses);
        }

        /**
         * @return SQL string.
         */
        public String getSQL() {
            return idxCond.getSQL(false);
        }

        /**
         * @param tbl Table to compare values.
         * @param o Value to compare.
         * @return Compare result.
         */
        abstract boolean checkValue(Table tbl, Value o);

        /**
         * @param ses Session.
         * @param idxCond Index condition to create checker.
         * @return Condition checker.
         */
        static ConditionChecker create(Session ses, IndexCondition idxCond) {
            ConditionChecker checker;

            switch (idxCond.getCompareType()) {
                case Comparison.IN_LIST:
                case Comparison.IN_QUERY:
                case Comparison.SPATIAL_INTERSECTS:
                case Comparison.FALSE:
                    return null;

                case Comparison.EQUAL:
                case Comparison.EQUAL_NULL_SAFE:
                    checker = new ConditionEqualChecker();
                    break;

                case Comparison.BIGGER_EQUAL:
                    checker = new ConditionBiggerEqualChecker();
                    break;

                case Comparison.BIGGER:
                    checker = new ConditionBiggerChecker();
                    break;

                case Comparison.SMALLER_EQUAL:
                    checker = new ConditionSmallerEqualChecker();
                    break;

                case Comparison.SMALLER:
                    checker = new ConditionSmallerChecker();
                    break;

                default:
                    throw DbException.throwInternalError("type=" + idxCond.getCompareType());
            }

            if (checker != null) {
                checker.colId = idxCond.getColumn().getColumnId();
                checker.idxCond = idxCond;
            }

            return checker;
        }
    }

    /**
     *
     */
    private static class ConditionEqualChecker extends ConditionChecker {
        /** {@inheritDoc} */
        @Override boolean checkValue(Table tbl, Value o) {
            return tbl.compareValues(o, v) == 0;
        }
    }

    /**
     *
     */
    private static class ConditionBiggerEqualChecker extends ConditionChecker {
        /** {@inheritDoc} */
        @Override boolean checkValue(Table tbl, Value o) {
            return tbl.compareValues(o, v) >= 0;
        }
    }

    /**
     *
     */
    private static class ConditionBiggerChecker extends ConditionChecker {
        /** {@inheritDoc} */
        @Override boolean checkValue(Table tbl, Value o) {
            return tbl.compareValues(o, v) > 0;
        }
    }

    /**
     *
     */
    private static class ConditionSmallerChecker extends ConditionChecker {
        /** {@inheritDoc} */
        @Override boolean checkValue(Table tbl, Value o) {
            return tbl.compareValues(o, v) < 0;
        }
    }

    /**
     *
     */
    private static class ConditionSmallerEqualChecker extends ConditionChecker {
        /** {@inheritDoc} */
        @Override boolean checkValue(Table tbl, Value o) {
            return tbl.compareValues(o, v) <= 0;
        }
    }
}
