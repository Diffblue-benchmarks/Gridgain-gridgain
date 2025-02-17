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

package org.apache.ignite.internal.processors.query.h2.sys.view;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.apache.ignite.internal.GridKernalContext;
import org.apache.ignite.internal.stat.IoStatisticsHolder;
import org.apache.ignite.internal.stat.IoStatisticsHolderCache;
import org.apache.ignite.internal.stat.IoStatisticsHolderKey;
import org.apache.ignite.internal.stat.IoStatisticsType;
import org.h2.engine.Session;
import org.h2.result.Row;
import org.h2.result.SearchRow;
import org.h2.value.Value;

/**
 * System view of cache group IO statistics.
 */
public class SqlSystemViewCacheGroupsIOStatistics extends SqlAbstractLocalSystemView {
    /**
     * @param ctx Grid context.
     */
    public SqlSystemViewCacheGroupsIOStatistics(GridKernalContext ctx) {
        super("LOCAL_CACHE_GROUPS_IO", "Local node IO statistics for cache groups", ctx, "CACHE_GROUP_NAME",
            newColumn("CACHE_GROUP_ID", Value.INT),
            newColumn("CACHE_GROUP_NAME"),
            newColumn("PHYSICAL_READS", Value.LONG),
            newColumn("LOGICAL_READS", Value.LONG)
        );
    }

    /** {@inheritDoc} */
    @Override public Iterator<Row> getRows(Session ses, SearchRow first, SearchRow last) {
        SqlSystemViewColumnCondition nameCond = conditionForColumn("CACHE_GROUP_NAME", first, last);

        Map<IoStatisticsHolderKey, IoStatisticsHolder> stats = ctx.ioStats().statistics(IoStatisticsType.CACHE_GROUP);

        List<Row> rows = new ArrayList<>();

        if (nameCond.isEquality()) {
            String cacheGrpName = nameCond.valueForEquality().getString();

            IoStatisticsHolderCache statHolder = (IoStatisticsHolderCache)stats
                .get(new IoStatisticsHolderKey(cacheGrpName));

            if (statHolder != null) {
                rows.add(
                    createRow(
                        ses,
                        statHolder.cacheGroupId(),
                        cacheGrpName,
                        statHolder.physicalReads(),
                        statHolder.logicalReads()
                    )
                );
            }
        }
        else {
            for (Map.Entry<IoStatisticsHolderKey, IoStatisticsHolder> entry : stats.entrySet()) {
                IoStatisticsHolderCache statHolder = (IoStatisticsHolderCache)entry.getValue();

                rows.add(
                    createRow(
                        ses,
                        statHolder.cacheGroupId(),
                        entry.getKey().name(),
                        statHolder.physicalReads(),
                        statHolder.logicalReads()
                    )
                );
            }
        }

        return rows.iterator();
    }

    /** {@inheritDoc} */
    @Override public boolean canGetRowCount() {
        return true;
    }

    /** {@inheritDoc} */
    @Override public long getRowCount() {
        return ctx.ioStats().statistics(IoStatisticsType.CACHE_GROUP).size();
    }
}
