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

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.ignite.IgniteSystemProperties;
import org.apache.ignite.internal.processors.cache.persistence.IgnitePdsTaskCancelingTest;
import org.apache.ignite.internal.processors.cache.persistence.db.IgnitePdsPartitionPreloadTest;
import org.apache.ignite.internal.processors.cache.persistence.file.FileDownloaderTest;
import org.apache.ignite.testframework.junits.DynamicSuite;
import org.junit.runner.RunWith;

/**
 * Mvcc variant of {@link IgnitePdsTestSuite4}.
 */
@RunWith(DynamicSuite.class)
public class IgnitePdsMvccTestSuite4 {
    /**
     * @return Suite.
     */
    public static List<Class<?>> suite() {
        System.setProperty(IgniteSystemProperties.IGNITE_FORCE_MVCC_MODE_IN_TESTS, "true");

        Set<Class> ignoredTests = new HashSet<>();

        // Skip classes that already contains Mvcc tests
        ignoredTests.add(IgnitePdsPartitionPreloadTest.class);

        // Skip irrelevant test
        ignoredTests.add(FileDownloaderTest.class);
        ignoredTests.add(IgnitePdsTaskCancelingTest.class);

        return IgnitePdsTestSuite4.suite(ignoredTests);
    }
}
