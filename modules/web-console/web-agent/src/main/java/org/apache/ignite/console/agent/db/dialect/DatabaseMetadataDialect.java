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

package org.apache.ignite.console.agent.db.dialect;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.ignite.cache.QueryIndex;
import org.apache.ignite.cache.QueryIndexType;
import org.apache.ignite.console.agent.db.DbColumn;
import org.apache.ignite.console.agent.db.DbTable;
import org.apache.ignite.internal.visor.query.VisorQueryIndex;

/**
 * Base class for database metadata dialect.
 */
public abstract class DatabaseMetadataDialect {
    /**
     * Gets schemas from database.
     *
     * @param conn Database connection.
     * @return Collection of schema descriptors.
     * @throws SQLException If failed to get schemas.
     */
    public abstract Collection<String> schemas(Connection conn) throws SQLException;

    /**
     * Gets tables from database.
     *
     * @param conn Database connection.
     * @param schemas Collection of schema names to load.
     * @param tblsOnly If {@code true} then gets only tables otherwise gets tables and views.
     * @return Collection of table descriptors.
     * @throws SQLException If failed to get tables.
     */
    public abstract Collection<DbTable> tables(Connection conn, List<String> schemas, boolean tblsOnly)
        throws SQLException;

    /**
     * @return Collection of database system schemas.
     */
    public Set<String> systemSchemas() {
        return Collections.singleton("INFORMATION_SCHEMA");
    }

    /**
     * @return Collection of unsigned type names.
     * @throws SQLException If failed to get unsigned type names.
     */
    public Set<String> unsignedTypes(DatabaseMetaData dbMeta) throws SQLException {
        return Collections.emptySet();
    }

    /**
     * Create table descriptor.
     *
     * @param schema Schema name.
     * @param tbl Table name.
     * @param cols Table columns.
     * @param idxs Table indexes.
     * @return New {@code DbTable} instance.
     */
    protected DbTable table(String schema, String tbl, Collection<DbColumn> cols, Collection<QueryIndex>idxs) {
        Collection<VisorQueryIndex> res = new ArrayList<>(idxs.size());

        for (QueryIndex idx : idxs)
            res.add(new VisorQueryIndex(idx));

        return new DbTable(schema, tbl, cols, res);
    }

    /**
     * Create index descriptor.
     *
     * @param idxName Index name.
     * @return New initialized {@code QueryIndex} instance.
     */
    protected QueryIndex index(String idxName) {
        QueryIndex idx = new QueryIndex();

        idx.setName(idxName);
        idx.setIndexType(QueryIndexType.SORTED);
        idx.setFields(new LinkedHashMap<String, Boolean>());

        return idx;
    }

    /**
     * Select first shortest index.
     *
     * @param uniqueIdxs Unique indexes with columns.
     * @return Unique index that could be used instead of primary key.
     */
    protected Map.Entry<String, Set<String>> uniqueIndexAsPk(Map<String, Set<String>> uniqueIdxs) {
        Map.Entry<String, Set<String>> uniqueIdxAsPk = null;

        for (Map.Entry<String, Set<String>> uniqueIdx : uniqueIdxs.entrySet()) {
            if (uniqueIdxAsPk == null || uniqueIdxAsPk.getValue().size() > uniqueIdx.getValue().size())
                uniqueIdxAsPk = uniqueIdx;
        }

        return uniqueIdxAsPk;
    }
}
