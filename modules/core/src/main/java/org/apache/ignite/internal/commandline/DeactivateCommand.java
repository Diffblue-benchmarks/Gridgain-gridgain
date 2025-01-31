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

package org.apache.ignite.internal.commandline;

import org.apache.ignite.internal.client.GridClient;
import org.apache.ignite.internal.client.GridClientClusterState;
import org.apache.ignite.internal.client.GridClientConfiguration;

import static org.apache.ignite.internal.commandline.CommandList.DEACTIVATE;
import static org.apache.ignite.internal.commandline.CommandLogger.optional;
import static org.apache.ignite.internal.commandline.CommonArgParser.CMD_AUTO_CONFIRMATION;

/**
 * Command to deactivate cluster.
 */
public class DeactivateCommand implements Command<Void> {
    /** {@inheritDoc} */
    @Override public void printUsage(CommandLogger logger) {
        Command.usage(logger, "Deactivate cluster:", DEACTIVATE, optional(CMD_AUTO_CONFIRMATION));
    }

    /** {@inheritDoc} */
    @Override public String confirmationPrompt() {
        return "Warning: the command will deactivate a cluster.";
    }

    /**
     * Deactivate cluster.
     *
     * @param clientCfg Client configuration.
     * @throws Exception If failed to deactivate.
     */
    @Override public Object execute(GridClientConfiguration clientCfg, CommandLogger logger) throws Exception {
        try (GridClient client = Command.startClient(clientCfg)) {
            GridClientClusterState state = client.state();

            state.active(false);

            logger.log("Cluster deactivated");
        }
        catch (Exception e) {
            logger.log("Failed to deactivate cluster.");

            throw e;
        }

        return null;
    }

    /** {@inheritDoc} */
    @Override public Void arg() {
        return null;
    }
}
