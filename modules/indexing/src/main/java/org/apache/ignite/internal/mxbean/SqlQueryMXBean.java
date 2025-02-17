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

package org.apache.ignite.internal.mxbean;

import org.apache.ignite.mxbean.MXBeanDescription;
import org.apache.ignite.mxbean.MXBeanParametersDescriptions;
import org.apache.ignite.mxbean.MXBeanParametersNames;

/**
 * An MX bean allowing to monitor and tune SQL queries.
 *
 * @deprecated Temporary monitoring solution.
 */
@Deprecated
public interface SqlQueryMXBean {
    /**
     * @return Timeout in milliseconds after which long query warning will be printed.
     */
    @MXBeanDescription("Timeout in milliseconds after which long query warning will be printed.")
    long getLongQueryWarningTimeout();

    /**
     * Sets timeout in milliseconds after which long query warning will be printed.
     *
     * @param longQueryWarningTimeout Timeout in milliseconds after which long query warning will be printed.
     */
    @MXBeanDescription("Sets timeout in milliseconds after which long query warning will be printed.")
    @MXBeanParametersNames("longQueryWarningTimeout")
    @MXBeanParametersDescriptions("Timeout in milliseconds after which long query warning will be printed.")
    void setLongQueryWarningTimeout(long longQueryWarningTimeout);

    /**
     * @return Long query timeout multiplier.
     */
    @MXBeanDescription("Long query timeout multiplier. The warning will be printed after: timeout, " +
        "timeout * multiplier, timeout * multiplier * multiplier, etc. " +
        "If the multiplier <= 1, the warning message is printed once.")
    int getLongQueryTimeoutMultiplier();

    /**
     * Sets long query timeout multiplier. The warning will be printed after:
     *      - timeout;
     *      - timeout * multiplier;
     *      - timeout * multiplier * multiplier;
     *      - etc.
     * If the multiplier <= 1, the warning message is printed once.
     *
     * @param longQueryTimeoutMultiplier Long query timeout multiplier.
     */
    @MXBeanDescription("Sets long query timeout multiplier. The warning will be printed after: timeout, " +
        "timeout * multiplier, timeout * multiplier * multiplier, etc. " +
        "If the multiplier <= 1, the warning message is printed once.")
    @MXBeanParametersNames("longQueryTimeoutMultiplier")
    @MXBeanParametersDescriptions("Long query timeout multiplier.")
    void setLongQueryTimeoutMultiplier(int longQueryTimeoutMultiplier);
}
