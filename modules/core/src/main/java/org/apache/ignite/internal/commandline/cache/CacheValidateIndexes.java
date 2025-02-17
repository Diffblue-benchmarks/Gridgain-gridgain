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


package org.apache.ignite.internal.commandline.cache;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.apache.ignite.internal.client.GridClient;
import org.apache.ignite.internal.client.GridClientConfiguration;
import org.apache.ignite.internal.commandline.Command;
import org.apache.ignite.internal.commandline.CommandArgIterator;
import org.apache.ignite.internal.commandline.CommandLogger;
import org.apache.ignite.internal.commandline.argument.CommandArgUtils;
import org.apache.ignite.internal.commandline.cache.argument.ValidateIndexesCommandArg;
import org.apache.ignite.internal.processors.cache.verify.PartitionKey;
import org.apache.ignite.internal.util.typedef.F;
import org.apache.ignite.internal.util.typedef.internal.U;
import org.apache.ignite.internal.visor.verify.IndexIntegrityCheckIssue;
import org.apache.ignite.internal.visor.verify.IndexValidationIssue;
import org.apache.ignite.internal.visor.verify.ValidateIndexesPartitionResult;
import org.apache.ignite.internal.visor.verify.VisorValidateIndexesJobResult;
import org.apache.ignite.internal.visor.verify.VisorValidateIndexesTaskArg;
import org.apache.ignite.internal.visor.verify.VisorValidateIndexesTaskResult;

import static org.apache.ignite.internal.commandline.CommandLogger.optional;
import static org.apache.ignite.internal.commandline.CommandLogger.or;
import static org.apache.ignite.internal.commandline.TaskExecutor.executeTaskByNameOnNode;
import static org.apache.ignite.internal.commandline.cache.CacheCommandList.IDLE_VERIFY;
import static org.apache.ignite.internal.commandline.cache.CacheCommands.OP_NODE_ID;
import static org.apache.ignite.internal.commandline.cache.CacheCommands.usageCache;
import static org.apache.ignite.internal.commandline.cache.CacheSubcommands.VALIDATE_INDEXES;
import static org.apache.ignite.internal.commandline.cache.argument.IdleVerifyCommandArg.CACHE_FILTER;
import static org.apache.ignite.internal.commandline.cache.argument.IdleVerifyCommandArg.EXCLUDE_CACHES;
import static org.apache.ignite.internal.commandline.cache.argument.ValidateIndexesCommandArg.CHECK_FIRST;
import static org.apache.ignite.internal.commandline.cache.argument.ValidateIndexesCommandArg.CHECK_THROUGH;
import static org.apache.ignite.internal.processors.cache.GridCacheUtils.UTILITY_CACHE_NAME;

/**
 * Validate indexes command.
 */
public class CacheValidateIndexes implements Command<CacheValidateIndexes.Arguments> {
    /** {@inheritDoc} */
    @Override public void printUsage(CommandLogger logger) {
        String CACHES = "cacheName1,...,cacheNameN";
        String description = "Verify counters and hash sums of primary and backup partitions for the specified " +
            "caches/cache groups on an idle cluster and print out the differences, if any. " +
            "Cache filtering options configure the set of caches that will be processed by " + IDLE_VERIFY + " command. " +
            "Default value for the set of cache names (or cache group names) is all cache groups. Default value for " +
            EXCLUDE_CACHES + " is empty set. Default value for " + CACHE_FILTER + " is no filtering. Therefore, " +
            "the set of all caches is sequently filtered by cache name " +
            "regexps, by cache type and after all by exclude regexps.";

        Map<String, String> map = U.newLinkedHashMap(16);

        map.put(CHECK_FIRST + " N", "validate only the first N keys");
        map.put(CHECK_THROUGH + " K", "validate every Kth key");

        usageCache(logger, VALIDATE_INDEXES, description, map,
            optional(CACHES), OP_NODE_ID, optional(or(CHECK_FIRST + " N", CHECK_THROUGH + " K")));
    }

    /**
     * Container for command arguments.
     */
    public class Arguments {
         /** Caches. */
        private Set<String> caches;

        /** Node id. */
        private UUID nodeId;

        /** Max number of entries to be checked. */
        private int checkFirst = -1;

        /** Number of entries to check through. */
        private int checkThrough = -1;

        /**
         *
         */
        public Arguments(Set<String> caches, UUID nodeId, int checkFirst, int checkThrough) {
            this.caches = caches;
            this.nodeId = nodeId;
            this.checkFirst = checkFirst;
            this.checkThrough = checkThrough;
        }

        /**
         * @return Caches.
         */
        public Set<String> caches() {
            return caches;
        }

        /**
         * @return Max number of entries to be checked.
         */
        public int checkFirst() {
            return checkFirst;
        }

        /**
         * @return Number of entries to check through.
         */
        public int checkThrough() {
            return checkThrough;
        }


        /**
         * @return Node id.
         */
        public UUID nodeId() {
            return nodeId;
        }
    }

    /** Command parsed arguments. */
    private Arguments args;

    /** {@inheritDoc} */
    @Override public Arguments arg() {
        return args;
    }

    /** {@inheritDoc} */
    @Override public Object execute(GridClientConfiguration clientCfg, CommandLogger logger) throws Exception {
        VisorValidateIndexesTaskArg taskArg = new VisorValidateIndexesTaskArg(
            args.caches(),
            args.nodeId() != null ? Collections.singleton(args.nodeId()) : null,
            args.checkFirst(),
            args.checkThrough()
        );

        try (GridClient client = Command.startClient(clientCfg)) {
            VisorValidateIndexesTaskResult taskRes = executeTaskByNameOnNode(
                client, "org.apache.ignite.internal.visor.verify.VisorValidateIndexesTask", taskArg, null, clientCfg);

            boolean errors = logger.printErrors(taskRes.exceptions(), "Index validation failed on nodes:");

            for (Map.Entry<UUID, VisorValidateIndexesJobResult> nodeEntry : taskRes.results().entrySet()) {
                if (!nodeEntry.getValue().hasIssues())
                    continue;

                errors = true;

                logger.log("Index issues found on node " + nodeEntry.getKey() + ":");

                Collection<IndexIntegrityCheckIssue> integrityCheckFailures = nodeEntry.getValue().integrityCheckFailures();

                if (!integrityCheckFailures.isEmpty()) {
                    for (IndexIntegrityCheckIssue is : integrityCheckFailures)
                        logger.logWithIndent(is);
                }

                Map<PartitionKey, ValidateIndexesPartitionResult> partRes = nodeEntry.getValue().partitionResult();

                for (Map.Entry<PartitionKey, ValidateIndexesPartitionResult> e : partRes.entrySet()) {
                    ValidateIndexesPartitionResult res = e.getValue();

                    if (!res.issues().isEmpty()) {
                        logger.logWithIndent(CommandLogger.join(" ", e.getKey(), e.getValue()));

                        for (IndexValidationIssue is : res.issues())
                            logger.logWithIndent(is, 2);
                    }
                }

                Map<String, ValidateIndexesPartitionResult> idxRes = nodeEntry.getValue().indexResult();

                for (Map.Entry<String, ValidateIndexesPartitionResult> e : idxRes.entrySet()) {
                    ValidateIndexesPartitionResult res = e.getValue();

                    if (!res.issues().isEmpty()) {
                        logger.logWithIndent(CommandLogger.join(" ", "SQL Index", e.getKey(), e.getValue()));

                        for (IndexValidationIssue is : res.issues())
                            logger.logWithIndent(is, 2);
                    }
                }
            }

            if (!errors)
                logger.log("no issues found.");
            else
                logger.log("issues found (listed above).");

            logger.nl();

            return taskRes;
        }
    }

    /** {@inheritDoc} */
    @Override public void parseArguments(CommandArgIterator argIter) {
        int checkFirst = -1;
        int checkThrough = -1;
        UUID nodeId = null;
        Set<String> caches = null;

        int argsCnt = 0;

        while (argIter.hasNextSubArg() && argsCnt++ < 4) {
            String nextArg = argIter.nextArg("");

            ValidateIndexesCommandArg arg = CommandArgUtils.of(nextArg, ValidateIndexesCommandArg.class);

            if (arg == CHECK_FIRST || arg == CHECK_THROUGH) {
                if (!argIter.hasNextSubArg())
                    throw new IllegalArgumentException("Numeric value for '" + nextArg + "' parameter expected.");

                int numVal;

                String numStr = argIter.nextArg("");

                try {
                    numVal = Integer.parseInt(numStr);
                }
                catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException(
                        "Not numeric value was passed for '" + nextArg + "' parameter: " + numStr
                    );
                }

                if (numVal <= 0)
                    throw new IllegalArgumentException("Value for '" + nextArg + "' property should be positive.");

                if (arg == CHECK_FIRST)
                    checkFirst = numVal;
                else
                    checkThrough = numVal;

                continue;
            }

            try {
                nodeId = UUID.fromString(nextArg);

                continue;
            }
            catch (IllegalArgumentException ignored) {
                //No-op.
            }

            caches = argIter.parseStringSet(nextArg);

            if (F.constainsStringIgnoreCase(caches, UTILITY_CACHE_NAME)) {
                throw new IllegalArgumentException(
                    VALIDATE_INDEXES + " not allowed for `" + UTILITY_CACHE_NAME + "` cache."
                );
            }
        }

        args = new Arguments(caches, nodeId, checkFirst, checkThrough);
    }
}
