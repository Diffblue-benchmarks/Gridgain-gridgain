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

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.apache.ignite.configuration.DataRegionConfiguration;
import org.apache.ignite.configuration.DataStorageConfiguration;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.configuration.WALMode;
import org.apache.ignite.internal.util.typedef.F;

/**
 * Check logging local node metrics with PDS enabled.
 */
public class GridNodeMetricsLogPdsSelfTest extends GridNodeMetricsLogSelfTest {
    /** {@inheritDoc} */
    @Override protected IgniteConfiguration getConfiguration(String igniteInstanceName) throws Exception {
        IgniteConfiguration cfg = super.getConfiguration(igniteInstanceName);

        DataStorageConfiguration memCfg = new DataStorageConfiguration()
            .setDefaultDataRegionConfiguration(
                new DataRegionConfiguration()
                    .setMaxSize(30 * 1024 * 1024)
                    .setPersistenceEnabled(true))
            .setWalMode(WALMode.LOG_ONLY);

        cfg.setDataStorageConfiguration(memCfg);

        return cfg;
    }

    /** {@inheritDoc} */
    @Override protected void beforeTest() throws Exception {
        cleanPersistenceDir();

        super.beforeTest();

        grid(0).cluster().active(true);
    }

    /** {@inheritDoc} */
    @Override protected void afterTest() throws Exception {
        super.afterTest();

        cleanPersistenceDir();
    }

    /** {@inheritDoc} */
    @Override protected void checkNodeMetricsFormat(String logOutput) {
        super.checkNodeMetricsFormat(logOutput);

        String msg = "Metrics are missing in the log or have an unexpected format";

        assertTrue(msg, logOutput.matches("(?s).*Ignite persistence \\[used=.*].*"));
    }

    /** {@inheritDoc} */
    @Override protected void checkMemoryMetrics(String logOutput) {
        super.checkMemoryMetrics(logOutput);

        boolean summaryFmtMatches = false;

        Set<String> regions = new HashSet<>();

        Pattern ptrn = Pattern.compile("(?m).{2,}( {3}(?<name>.+) region|Ignite persistence) " +
            "\\[used=(?<used>[-.\\d]+)?.*]");

        Matcher matcher = ptrn.matcher(logOutput);

        while (matcher.find()) {
            String subj = logOutput.substring(matcher.start(), matcher.end());

            assertFalse("\"used\" cannot be empty: " + subj, F.isEmpty(matcher.group("used")));

            String usedSize = matcher.group("used");

            int used = Integer.parseInt(usedSize);

            assertTrue(used + " should be non negative: " + subj, used >= 0);

            String regName = matcher.group("name");

            if (F.isEmpty(regName))
                summaryFmtMatches = true;
            else
                regions.add(regName);
        }

        assertTrue("Persistence metrics have unexpected format.", summaryFmtMatches);

        Set<String> expRegions = grid(0).context().cache().context().database().dataRegions().stream()
            .filter(v -> v.config().isPersistenceEnabled())
            .map(v -> v.config().getName().trim())
            .collect(Collectors.toSet());

        assertEquals(expRegions, regions);
    }
}
