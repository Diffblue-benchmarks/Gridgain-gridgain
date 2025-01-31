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

package org.apache.ignite.testsuites;

import org.apache.ignite.internal.processors.cache.query.continuous.CacheContinuousQueryAsyncFailoverAtomicSelfTest;
import org.apache.ignite.internal.processors.cache.query.continuous.CacheContinuousQueryAsyncFailoverMvccTxSelfTest;
import org.apache.ignite.internal.processors.cache.query.continuous.CacheContinuousQueryAsyncFailoverTxReplicatedSelfTest;
import org.apache.ignite.internal.processors.cache.query.continuous.CacheContinuousQueryAsyncFailoverTxSelfTest;
import org.apache.ignite.internal.processors.cache.query.continuous.CacheContinuousQueryFailoverAtomicReplicatedSelfTest;
import org.apache.ignite.internal.processors.cache.query.continuous.CacheContinuousQueryFailoverAtomicSelfTest;
import org.apache.ignite.internal.processors.cache.query.continuous.CacheContinuousQueryFailoverMvccTxReplicatedSelfTest;
import org.apache.ignite.internal.processors.cache.query.continuous.CacheContinuousQueryFailoverMvccTxSelfTest;
import org.apache.ignite.internal.processors.cache.query.continuous.CacheContinuousQueryFailoverTxReplicatedSelfTest;
import org.apache.ignite.internal.processors.cache.query.continuous.CacheContinuousQueryFailoverTxSelfTest;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;

/**
 * Test suite for cache queries.
 */
@RunWith(Suite.class)
@Suite.SuiteClasses({
    // Continuous queries failover tests.
    CacheContinuousQueryFailoverAtomicSelfTest.class,
    CacheContinuousQueryFailoverAtomicReplicatedSelfTest.class,
    CacheContinuousQueryFailoverTxSelfTest.class,
    CacheContinuousQueryFailoverTxReplicatedSelfTest.class,
    CacheContinuousQueryFailoverMvccTxSelfTest.class,
    CacheContinuousQueryFailoverMvccTxReplicatedSelfTest.class,

    CacheContinuousQueryAsyncFailoverAtomicSelfTest.class,
    CacheContinuousQueryAsyncFailoverTxReplicatedSelfTest.class,
    CacheContinuousQueryAsyncFailoverTxSelfTest.class,
    CacheContinuousQueryAsyncFailoverMvccTxSelfTest.class
})
public class IgniteCacheQuerySelfTestSuite4 {
}
