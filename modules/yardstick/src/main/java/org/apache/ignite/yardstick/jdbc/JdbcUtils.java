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

package org.apache.ignite.yardstick.jdbc;

import org.apache.ignite.IgniteSemaphore;
import org.apache.ignite.cache.CacheAtomicityMode;
import org.apache.ignite.cache.query.SqlFieldsQuery;
import org.apache.ignite.internal.IgniteEx;
import org.apache.ignite.internal.processors.query.GridQueryProcessor;
import org.yardstickframework.BenchmarkConfiguration;

import static org.yardstickframework.BenchmarkUtils.println;

/**
 * Useful methods for JDBC benchmarks.
 */
public class JdbcUtils {
    /**
     * Common method to fill test stand with data.
     *
     * @param cfg Benchmark configuration.
     * @param ignite Ignite node.
     * @param range Data key range.
     */
    public static void fillData(BenchmarkConfiguration cfg, IgniteEx ignite, long range,
        CacheAtomicityMode atomicMode) {
        IgniteSemaphore sem = ignite.semaphore("sql-setup", 1, true, true);

        try {
            if (sem.tryAcquire()) {
                println(cfg, "Create table...");

                StringBuilder qry = new StringBuilder("CREATE TABLE test_long (id LONG PRIMARY KEY, val LONG)" +
                    " WITH \"wrap_value=true");

                if (atomicMode != null)
                    qry.append(", atomicity=").append(atomicMode.name());

                qry.append("\";");

                String qryStr = qry.toString();

                println(cfg, "Creating table with schema: " + qryStr);

                GridQueryProcessor qProc = ignite.context().query();

                qProc.querySqlFields(
                    new SqlFieldsQuery(qryStr), true);

                println(cfg, "Populate data...");

                for (long l = 1; l <= range; ++l) {
                    qProc.querySqlFields(
                        new SqlFieldsQuery("INSERT INTO test_long (id, val) VALUES (?, ?)")
                            .setArgs(l, l + 1), true);

                    if (l % 10000 == 0)
                        println(cfg, "Populate " + l);
                }

                qProc.querySqlFields(new SqlFieldsQuery("CREATE INDEX val_idx ON test_long (val)"), true);

                println(cfg, "Finished populating data");
            }
            else {
                // Acquire (wait setup by other client) and immediately release/
                println(cfg, "Waits for setup...");

                sem.acquire();
            }
        }
        finally {
            sem.release();
        }
    }
}
