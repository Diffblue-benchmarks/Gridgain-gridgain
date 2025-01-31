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

package org.apache.ignite.internal;

import java.util.Map;
import org.apache.ignite.mxbean.TransactionMetricsMxBean;
import org.apache.ignite.transactions.TransactionMetrics;

/**
 * Transactions MXBean implementation.
 */
public class TransactionMetricsMxBeanImpl implements TransactionMetricsMxBean {
    /** */
    private static final long serialVersionUID = 0L;

    /** */
    private final TransactionMetrics transactionMetrics;

    /**
     * Create TransactionMetricsMxBeanImpl.
     */
    public TransactionMetricsMxBeanImpl() {
        this(null);
    }

    /**
     * @param transactionMetrics Transaction metrics.
     */
    public TransactionMetricsMxBeanImpl(TransactionMetrics transactionMetrics) {
        this.transactionMetrics = transactionMetrics;
    }

    /** {@inheritDoc} */
    @Override public long commitTime() {
        return transactionMetrics.commitTime();
    }

    /** {@inheritDoc} */
    @Override public long rollbackTime() {
        return transactionMetrics.rollbackTime();
    }

    /** {@inheritDoc} */
    @Override public int txCommits() {
        return transactionMetrics.txCommits();
    }

    /** {@inheritDoc} */
    @Override public int txRollbacks() {
        return transactionMetrics.txRollbacks();
    }

    /** {@inheritDoc} */
    @Override public Map<String, String> getAllOwnerTransactions() {
        return transactionMetrics.getAllOwnerTransactions();
    }

    /** {@inheritDoc} */
    @Override public Map<String, String> getLongRunningOwnerTransactions(int duration) {
        return transactionMetrics.getLongRunningOwnerTransactions(duration);
    }

    /** {@inheritDoc} */
    @Override public long getTransactionsCommittedNumber() {
        return transactionMetrics.getTransactionsCommittedNumber();
    }

    /** {@inheritDoc} */
    @Override public long getTransactionsRolledBackNumber() {
        return transactionMetrics.getTransactionsRolledBackNumber();
    }

    /** {@inheritDoc} */
    @Override public long getTransactionsHoldingLockNumber() {
        return transactionMetrics.getTransactionsHoldingLockNumber();
    }

    /** {@inheritDoc} */
    @Override public long getLockedKeysNumber() {
        return transactionMetrics.getLockedKeysNumber();
    }

    /** {@inheritDoc} */
    @Override public long getOwnerTransactionsNumber() {
        return transactionMetrics.getOwnerTransactionsNumber();
    }
}


