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

package org.apache.ignite.console.agent.handlers;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.apache.ignite.console.agent.AgentConfiguration;
import org.apache.ignite.console.agent.rest.RestExecutor;
import org.apache.ignite.console.agent.rest.RestResult;
import org.apache.ignite.console.demo.AgentClusterDemo;
import org.apache.ignite.internal.util.typedef.F;
import org.apache.ignite.internal.util.typedef.internal.U;

/**
 * API to translate REST requests to Ignite cluster.
 */
public class RestListener extends AbstractListener {
    /** */
    private final AgentConfiguration cfg;

    /** */
    private final RestExecutor restExecutor;

    /**
     * @param cfg Config.
     * @param restExecutor REST executor.
     */
    public RestListener(AgentConfiguration cfg, RestExecutor restExecutor) {
        this.cfg = cfg;
        this.restExecutor = restExecutor;
    }

    /** {@inheritDoc} */
    @Override protected ExecutorService newThreadPool() {
        return Executors.newCachedThreadPool();
    }

    /** {@inheritDoc} */
    @Override public Object execute(Map<String, Object> args) {
        if (log.isDebugEnabled())
            log.debug("Start parse REST command args: " + args);

        Map<String, Object> params = null;

        if (args.containsKey("params"))
            params = (Map<String, Object>)args.get("params");

        if (!args.containsKey("demo"))
            throw new IllegalArgumentException("Missing demo flag in arguments: " + args);

        boolean demo = (boolean)args.get("demo");

        if (F.isEmpty((String)args.get("token")))
            return RestResult.fail(401, "Request does not contain user token.");

        Map<String, Object> headers = null;

        if (args.containsKey("headers"))
            headers = (Map<String, Object>)args.get("headers");

        try {
            if (demo) {
                if (AgentClusterDemo.getDemoUrl() == null) {
                    if (cfg.disableDemo())
                        return RestResult.fail(404, "Demo mode disabled by administrator.");

                    AgentClusterDemo.tryStart().await();

                    if (AgentClusterDemo.getDemoUrl() == null)
                        return RestResult.fail(404, "Failed to send request because of embedded node for demo mode is not started yet.");
                }

                return restExecutor.sendRequest(AgentClusterDemo.getDemoUrl(), params, headers);
            }

            return restExecutor.sendRequest(this.cfg.nodeURIs(), params, headers);
        }
        catch (Exception e) {
            U.error(log, "Failed to execute REST command with parameters: " + params, e);

            return RestResult.fail(404, e.getMessage());
        }
    }
}
