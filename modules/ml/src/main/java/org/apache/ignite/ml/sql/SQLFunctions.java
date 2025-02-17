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

package org.apache.ignite.ml.sql;

import java.util.Map;
import java.util.concurrent.locks.LockSupport;
import org.apache.ignite.Ignition;
import org.apache.ignite.cache.query.annotations.QuerySqlFunction;
import org.apache.ignite.ml.inference.IgniteModelStorageUtil;
import org.apache.ignite.ml.inference.Model;
import org.apache.ignite.ml.math.primitives.vector.Vector;
import org.apache.ignite.ml.math.primitives.vector.VectorUtils;
import org.apache.ignite.ml.util.LRUCache;

/**
 * SQL functions that should be defined and passed into cache configuration to extend list of functions available
 * in SQL interface.
 */
public class SQLFunctions {
    /** Default LRU model cache size. */
    private static final int LRU_CACHE_SIZE = 10;

    /** Cache clear interval in seconds. */
    private static final long CACHE_CLEAR_INTERVAL_SEC = 60;

    /** Default LRU model cache. */
    // TODO: IGNITE-11163: Add hart beat tracker to DistributedInfModel.
    private static final Map<String, Model<Vector, Double>> cache = new LRUCache<>(LRU_CACHE_SIZE, Model::close);

    static {
        Thread invalidationThread = new Thread(() -> {
            while (Thread.currentThread().isInterrupted())
                LockSupport.parkNanos(CACHE_CLEAR_INTERVAL_SEC * 1_000_000_000L);

            synchronized (cache) {
                for (Model<Vector, Double> mdl : cache.values())
                    mdl.close();

                cache.clear();
            }
        });

        invalidationThread.setDaemon(true);
        invalidationThread.start();
    }

    /**
     * Makes prediction using specified model name to extract model from model storage and specified input values
     * as input object for prediction.
     *
     * @param mdl Pretrained model.
     * @param x Input values.
     * @return Prediction.
     */
    @QuerySqlFunction
    public static double predict(String mdl, Double... x) {
        Model<Vector, Double> infMdl;

        synchronized (cache) {
            infMdl = cache.computeIfAbsent(
                mdl,
                key -> IgniteModelStorageUtil.getModel(Ignition.ignite(), mdl)
            );
        }

        return infMdl.predict(VectorUtils.of(x));
    }
}
