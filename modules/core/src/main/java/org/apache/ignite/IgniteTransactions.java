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

package org.apache.ignite;

import java.util.Collection;
import org.apache.ignite.cache.CacheMode;
import org.apache.ignite.configuration.TransactionConfiguration;
import org.apache.ignite.transactions.Transaction;
import org.apache.ignite.transactions.TransactionConcurrency;
import org.apache.ignite.transactions.TransactionIsolation;
import org.apache.ignite.transactions.TransactionMetrics;

/**
 * Transactions facade provides ACID-compliant semantic when working with caches. You can
 * create a transaction when working with one cache or across multiple caches. Caches with
 * different cache modes, like {@link CacheMode#PARTITIONED PARTITIONED} or
 * {@link CacheMode#REPLICATED REPLICATED}, can also participate in the same transaction.
 * <p>
 * Transactions are {@link AutoCloseable}, so they will automatically rollback unless
 * explicitly committed.
 * <p>
 * Here is an example of a transaction:
 * <pre class="brush:java">
 * try (Transaction tx = Ignition.ignite().transactions().txStart()) {
 *   Account acct = cache.get(acctId);
 *
 *   // Current balance.
 *   double balance = acct.getBalance();
 *
 *   // Deposit $100 into account.
 *   acct.setBalance(balance + 100);
 *
 *   // Store updated account in cache.
 *   cache.put(acctId, acct);
 *
 *   tx.commit();
 * }
 * </pre>
 */
public interface IgniteTransactions {
    /**
     * Starts transaction with default isolation, concurrency, timeout, and invalidation policy.
     * All defaults are set in {@link TransactionConfiguration} at startup.
     *
     * @return New transaction
     * @throws IllegalStateException If transaction is already started by this thread.
     */
    public Transaction txStart() throws IllegalStateException;

    /**
     * Starts new transaction with the specified concurrency and isolation.
     *
     * @param concurrency Concurrency.
     * @param isolation Isolation.
     * @return New transaction.
     * @throws IllegalStateException If transaction is already started by this thread.
     */
    public Transaction txStart(TransactionConcurrency concurrency, TransactionIsolation isolation);

    /**
     * Starts transaction with specified isolation, concurrency, timeout, invalidation flag,
     * and number of participating entries.
     *
     * @param concurrency Concurrency.
     * @param isolation Isolation.
     * @param timeout Timeout.
     * @param txSize Number of entries participating in transaction (may be approximate).
     * @return New transaction.
     * @throws IllegalStateException If transaction is already started by this thread.
     */
    public Transaction txStart(TransactionConcurrency concurrency, TransactionIsolation isolation, long timeout,
        int txSize);

    /**
     * Gets transaction started by this thread or {@code null} if this thread does
     * not have a transaction.
     *
     * @return Transaction started by this thread or {@code null} if this thread
     *      does not have a transaction.
     */
    public Transaction tx();

    /**
     * @return Transaction metrics.
     */
    public TransactionMetrics metrics();

    /**
     * Resets transaction metrics.
     */
    public void resetMetrics();

    /**
     * Returns a list of active transactions initiated by this node.
     * <p>
     * Note: returned transaction handle will only support getters, {@link Transaction#close()},
     * {@link Transaction#rollback()}, {@link Transaction#rollbackAsync()} methods.
     * Trying to invoke other methods will lead to UnsupportedOperationException.
     *
     * @return Transactions started on local node.
     */
    public Collection<Transaction> localActiveTransactions();

    /**
     * Returns instance of Ignite Transactions to mark a transaction with a special label.
     *
     * @param lb label.
     * @return {@code This} for chaining.
     * @throws NullPointerException if label is null.
     */
    public IgniteTransactions withLabel(String lb);
}
