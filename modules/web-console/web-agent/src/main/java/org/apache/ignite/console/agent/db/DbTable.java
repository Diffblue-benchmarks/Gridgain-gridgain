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

package org.apache.ignite.console.agent.db;

import java.util.Collection;
import org.apache.ignite.internal.util.typedef.internal.S;
import org.apache.ignite.internal.visor.query.VisorQueryIndex;

/**
 * Database table.
 */
public class DbTable {
    /** Schema name. */
    private final String schema;

    /** Table name. */
    private final String tbl;

    /** Columns. */
    private final Collection<DbColumn> cols;

    /** Indexes. */
    private final Collection<VisorQueryIndex> idxs;

    /**
     * Default columns.
     *
     * @param schema Schema name.
     * @param tbl Table name.
     * @param cols Columns.
     * @param idxs Indexes;
     */
    public DbTable(String schema, String tbl, Collection<DbColumn> cols, Collection<VisorQueryIndex> idxs) {
        this.schema = schema;
        this.tbl = tbl;
        this.cols = cols;
        this.idxs = idxs;
    }

    /**
     * @return Schema name.
     */
    public String getSchema() {
        return schema;
    }

    /**
     * @return Table name.
     */
    public String getTable() {
        return tbl;
    }

    /**
     * @return Columns.
     */
    public Collection<DbColumn> getColumns() {
        return cols;
    }

    /**
     * @return Indexes.
     */
    public Collection<VisorQueryIndex> getIndexes() {
        return idxs;
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(DbTable.class, this);
    }
}
