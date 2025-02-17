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

package org.apache.ignite.internal.processors.query.h2.database;

import java.util.ArrayList;
import java.util.List;
import org.apache.ignite.IgniteLogger;
import org.apache.ignite.IgniteSystemProperties;
import org.apache.ignite.internal.processors.cache.persistence.tree.io.PageIO;
import org.apache.ignite.internal.processors.query.h2.opt.GridH2IndexBase;
import org.apache.ignite.internal.processors.query.h2.opt.GridH2Table;
import org.apache.ignite.internal.util.typedef.F;
import org.apache.ignite.internal.util.typedef.internal.U;
import org.h2.command.dml.AllColumnsForPlan;
import org.h2.engine.Session;
import org.h2.index.IndexType;
import org.h2.result.SortOrder;
import org.h2.table.IndexColumn;
import org.h2.table.Table;
import org.h2.table.TableFilter;

/**
 * H2 tree index base.
 */
public abstract class H2TreeIndexBase extends GridH2IndexBase {
    /** Default value for {@code IGNITE_MAX_INDEX_PAYLOAD_SIZE} */
    static final int IGNITE_MAX_INDEX_PAYLOAD_SIZE_DEFAULT = 10;

    /**
     * Constructor.
     *
     * @param tbl Table.
     * @param name Index name.
     * @param cols Indexed columns.
     * @param type Index type.
     */
    protected H2TreeIndexBase(GridH2Table tbl, String name, IndexColumn[] cols, IndexType type) {
        super(tbl, name, cols, type);
    }

    /**
     * @param affinityKey {@code true} for affinity key.
     * @param cacheName Cache name.
     * @param idxName Index name.
     * @param log Logger.
     * @param pk PK flag.
     * @param tbl table.
     * @param cols Columns array.
     * @return List of {@link InlineIndexHelper} objects.
     */
    static List<InlineIndexHelper> getAvailableInlineColumns(boolean affinityKey, String cacheName,
        String idxName, IgniteLogger log, boolean pk, Table tbl, IndexColumn[] cols) {
        List<InlineIndexHelper> res = new ArrayList<>();

        for (IndexColumn col : cols) {
            if (!InlineIndexHelper.AVAILABLE_TYPES.contains(col.column.getType().getValueType())) {
                String idxType = pk ? "PRIMARY KEY" : affinityKey ? "AFFINITY KEY (implicit)" : "SECONDARY";

                U.warn(log, "Column cannot be inlined into the index because it's type doesn't support inlining, " +
                    "index access may be slow due to additional page reads (change column type if possible) " +
                    "[cacheName=" + cacheName +
                    ", tableName=" + tbl.getName() +
                    ", idxName=" + idxName +
                    ", idxType=" + idxType +
                    ", colName=" + col.columnName +
                    ", columnType=" + InlineIndexHelper.nameTypeBycode(col.column.getType().getValueType()) + ']'
                );

                break;
            }

            InlineIndexHelper idx = new InlineIndexHelper(
                col.columnName,
                col.column.getType().getValueType(),
                col.column.getColumnId(),
                col.sortType,
                tbl.getCompareMode());

            res.add(idx);
        }

        return res;
    }

    /**
     * @return Inline size.
     */
    public abstract int inlineSize();

    /** {@inheritDoc} */
    @Override public double getCost(Session ses, int[] masks, TableFilter[] filters, int filter,
        SortOrder sortOrder, AllColumnsForPlan allColumnsSet) {

        long rowCnt = getRowCountApproximation();

        double baseCost = getCostRangeIndex(masks, rowCnt, filters, filter, sortOrder, false, allColumnsSet);

        int mul = getDistributedMultiplier(ses, filters, filter);

        return mul * baseCost;
    }

    /** {@inheritDoc} */
    @Override public boolean canGetFirstOrLast() {
        return true;
    }

    /** {@inheritDoc} */
    @Override public long getRowCountApproximation() {
        return 10_000; // TODO
    }

    /**
     * @param inlineIdxs Inline index helpers.
     * @param cfgInlineSize Inline size from cache config.
     * @param maxInlineSize Max inline size from cache config.
     * @return Inline size.
     */
    static int computeInlineSize(List<InlineIndexHelper> inlineIdxs, int cfgInlineSize, int maxInlineSize) {
        int propSize = maxInlineSize == -1 ? IgniteSystemProperties.getInteger(IgniteSystemProperties.IGNITE_MAX_INDEX_PAYLOAD_SIZE,
            IGNITE_MAX_INDEX_PAYLOAD_SIZE_DEFAULT) : maxInlineSize;

        if (cfgInlineSize == 0)
            return 0;

        if (F.isEmpty(inlineIdxs))
            return 0;

        if (cfgInlineSize == -1) {
            if (propSize == 0)
                return 0;

            int size = 0;

            for (InlineIndexHelper idxHelper : inlineIdxs) {
                if (idxHelper.size() <= 0) {
                    size = propSize;
                    break;
                }
                // 1 byte type + size
                size += idxHelper.size() + 1;
            }

            return Math.min(PageIO.MAX_PAYLOAD_SIZE, size);
        }
        else
            return Math.min(PageIO.MAX_PAYLOAD_SIZE, cfgInlineSize);
    }
}
