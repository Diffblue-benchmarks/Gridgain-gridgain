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

import org.apache.ignite.cache.query.SqlFieldsQuery;
import org.apache.ignite.internal.processors.cache.query.SqlFieldsQueryEx;
import org.apache.ignite.internal.processors.query.NestedTxMode;

import java.util.List;

/**
 * Query parameters which vary between requests having the same execution plan. Essentially, these are the arguments
 * of original {@link org.apache.ignite.cache.query.SqlFieldsQuery} which are not part of {@link QueryDescriptor}.
 */
public class QueryParameters {
    /** Arguments. */
    private final Object[] args;

    /** Partitions. */
    private final int[] parts;

    /** Timeout. */
    private final int timeout;

    /** Lazy flag. */
    private final boolean lazy;

    /** Page size. */
    private final int pageSize;

    /** Data page scan enabled flag. */
    private final Boolean dataPageScanEnabled;

    /** Nexted transactional mode. */
    private final NestedTxMode nestedTxMode;

    /** Auto-commit flag. */
    private final boolean autoCommit;

    /** Batched arguments. */
    private final List<Object[]> batchedArgs;

    /**
     * Update internal batch size.
     * Default is 1 to prevent deadlock on update where keys sequence are different in several concurrent updates.
     */
    private final int updateBatchSize;

    /**
     * Create parameters from query.
     *
     * @param qry Query.
     * @return Parameters.
     */
    public static QueryParameters fromQuery(SqlFieldsQuery qry) {
        NestedTxMode nestedTxMode = NestedTxMode.DEFAULT;
        boolean autoCommit = true;
        List<Object[]> batchedArgs = null;

        if (qry instanceof SqlFieldsQueryEx) {
            SqlFieldsQueryEx qry0 = (SqlFieldsQueryEx)qry;

            if (qry0.getNestedTxMode() != null)
                nestedTxMode = qry0.getNestedTxMode();

            autoCommit = qry0.isAutoCommit();

            batchedArgs = qry0.batchedArguments();
        }

        return new QueryParameters(
            qry.getArgs(),
            qry.getPartitions(),
            qry.getTimeout(),
            qry.isLazy(),
            qry.getPageSize(),
            qry.isDataPageScanEnabled(),
            nestedTxMode,
            autoCommit,
            batchedArgs,
            qry.getUpdateBatchSize()
        );
    }

    /**
     * Constructor.
     *
     * @param args Arguments.
     * @param parts Partitions.
     * @param timeout Timeout.
     * @param lazy Lazy flag.
     * @param pageSize Page size.
     * @param dataPageScanEnabled Data page scan enabled flag.
     * @param nestedTxMode Nested TX mode.
     * @param autoCommit Auto-commit flag.
     * @param batchedArgs Batched arguments.
     * @param updateBatchSize Update internal batch size.
     */
    @SuppressWarnings("AssignmentOrReturnOfFieldWithMutableType")
    private QueryParameters(
        Object[] args,
        int[] parts,
        int timeout,
        boolean lazy,
        int pageSize,
        Boolean dataPageScanEnabled,
        NestedTxMode nestedTxMode,
        boolean autoCommit,
        List<Object[]> batchedArgs,
        int updateBatchSize
    ) {
        this.args = args;
        this.parts = parts;
        this.timeout = timeout;
        this.lazy = lazy;
        this.pageSize = pageSize;
        this.dataPageScanEnabled = dataPageScanEnabled;
        this.nestedTxMode = nestedTxMode;
        this.autoCommit = autoCommit;
        this.batchedArgs = batchedArgs;
        this.updateBatchSize = updateBatchSize;
    }

    /**
     * @return Arguments.
     */
    @SuppressWarnings("AssignmentOrReturnOfFieldWithMutableType")
    public Object[] arguments() {
        return args;
    }

    /**
     * @return Partitions.
     */
    @SuppressWarnings("AssignmentOrReturnOfFieldWithMutableType")
    public int[] partitions() {
        return parts;
    }

    /**
     * @return Timeout.
     */
    public int timeout() {
        return timeout;
    }

    /**
     * @return Lazy flag.
     */
    public boolean lazy() {
        return lazy;
    }

    /**
     * @return Page size.
     */
    public int pageSize() {
        return pageSize;
    }

    /**
     * @return Data page scan enabled flag.
     */
    public Boolean dataPageScanEnabled() {
        return dataPageScanEnabled;
    }

    /**
     * @return Nested TX mode.
     */
    public NestedTxMode nestedTxMode() {
        return nestedTxMode;
    }

    /**
     * @return Auto-commit flag.
     */
    public boolean autoCommit() {
        return autoCommit;
    }

    /**
     * @return Batched arguments.
     */
    @SuppressWarnings("AssignmentOrReturnOfFieldWithMutableType")
    public List<Object[]> batchedArguments() {
        return batchedArgs;
    }

    /**
     * Gets update internal bach size.
     * Default is 1 to prevent deadlock on update where keys sequance are different in several concurrent updates.
     *
     * @return Update internal batch size
     */
    public int updateBatchSize() {
        return updateBatchSize;
    }

    /**
     * Convert current batched arguments to a form with single arguments.
     *
     * @param args Arguments.
     * @return Result.
     */
    public QueryParameters toSingleBatchedArguments(Object[] args) {
        return new QueryParameters(
            args,
            this.parts,
            this.timeout,
            this.lazy,
            this.pageSize,
            this.dataPageScanEnabled,
            this.nestedTxMode,
            this.autoCommit,
            null,
            this.updateBatchSize
        );
    }
}
