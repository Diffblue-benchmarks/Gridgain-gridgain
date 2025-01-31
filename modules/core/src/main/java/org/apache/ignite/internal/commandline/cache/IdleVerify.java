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

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.apache.ignite.IgniteException;
import org.apache.ignite.internal.IgniteNodeAttributes;
import org.apache.ignite.internal.client.GridClient;
import org.apache.ignite.internal.client.GridClientConfiguration;
import org.apache.ignite.internal.client.GridClientException;
import org.apache.ignite.internal.client.GridClientNode;
import org.apache.ignite.internal.commandline.Command;
import org.apache.ignite.internal.commandline.CommandArgIterator;
import org.apache.ignite.internal.commandline.CommandLogger;
import org.apache.ignite.internal.commandline.argument.CommandArgUtils;
import org.apache.ignite.internal.commandline.cache.argument.IdleVerifyCommandArg;
import org.apache.ignite.internal.processors.cache.verify.IdleVerifyResultV2;
import org.apache.ignite.internal.processors.cache.verify.PartitionHashRecord;
import org.apache.ignite.internal.processors.cache.verify.PartitionKey;
import org.apache.ignite.internal.processors.cache.verify.VerifyBackupPartitionsTaskV2;
import org.apache.ignite.internal.util.typedef.F;
import org.apache.ignite.internal.visor.verify.CacheFilterEnum;
import org.apache.ignite.internal.visor.verify.VisorIdleVerifyDumpTask;
import org.apache.ignite.internal.visor.verify.VisorIdleVerifyDumpTaskArg;
import org.apache.ignite.internal.visor.verify.VisorIdleVerifyTask;
import org.apache.ignite.internal.visor.verify.VisorIdleVerifyTaskArg;
import org.apache.ignite.internal.visor.verify.VisorIdleVerifyTaskResult;
import org.apache.ignite.internal.visor.verify.VisorIdleVerifyTaskV2;
import org.apache.ignite.lang.IgniteProductVersion;

import static java.lang.String.format;
import static org.apache.ignite.internal.commandline.CommandLogger.optional;
import static org.apache.ignite.internal.commandline.CommandLogger.or;
import static org.apache.ignite.internal.commandline.TaskExecutor.executeTask;
import static org.apache.ignite.internal.commandline.cache.CacheCommands.usageCache;
import static org.apache.ignite.internal.commandline.cache.CacheSubcommands.IDLE_VERIFY;
import static org.apache.ignite.internal.commandline.cache.argument.IdleVerifyCommandArg.CACHE_FILTER;
import static org.apache.ignite.internal.commandline.cache.argument.IdleVerifyCommandArg.CHECK_CRC;
import static org.apache.ignite.internal.commandline.cache.argument.IdleVerifyCommandArg.DUMP;
import static org.apache.ignite.internal.commandline.cache.argument.IdleVerifyCommandArg.EXCLUDE_CACHES;
import static org.apache.ignite.internal.commandline.cache.argument.IdleVerifyCommandArg.SKIP_ZEROS;
import static org.apache.ignite.internal.processors.cache.GridCacheUtils.UTILITY_CACHE_NAME;
import static org.apache.ignite.internal.visor.verify.CacheFilterEnum.ALL;
import static org.apache.ignite.internal.visor.verify.CacheFilterEnum.NOT_PERSISTENT;
import static org.apache.ignite.internal.visor.verify.CacheFilterEnum.PERSISTENT;
import static org.apache.ignite.internal.visor.verify.CacheFilterEnum.SYSTEM;
import static org.apache.ignite.internal.visor.verify.CacheFilterEnum.USER;

/**
 *
 */
public class IdleVerify implements Command<IdleVerify.Arguments> {
    /** {@inheritDoc} */
    @Override public void printUsage(CommandLogger logger) {
        String CACHES = "cacheName1,...,cacheNameN";
        String description = "Verify counters and hash sums of primary and backup partitions for the specified caches/cache " +
            "groups on an idle cluster and print out the differences, if any. When no parameters are specified, " +
            "all user caches are verified. Cache filtering options configure the set of caches that will be " +
            "processed by " + IDLE_VERIFY + " command. If cache names are specified, in form of regular " +
            "expressions, only matching caches will be verified. Caches matched by regexes specified after " +
            EXCLUDE_CACHES + " parameter will be excluded from verification. Using parameter " + CACHE_FILTER +
            " you can verify: only " + USER + " caches, only user " + PERSISTENT + " caches, only user " +
            NOT_PERSISTENT + " caches, only " + SYSTEM + " caches, or " + ALL + " of the above.";

        usageCache(logger,
            IDLE_VERIFY,
            description,
            Collections.singletonMap(CHECK_CRC.toString(),
                "check the CRC-sum of pages stored on disk before verifying data " +
                    "consistency in partitions between primary and backup nodes."),
            optional(DUMP), optional(SKIP_ZEROS), optional(CHECK_CRC), optional(EXCLUDE_CACHES, CACHES),
                optional(CACHE_FILTER, or(ALL, USER, SYSTEM, PERSISTENT, NOT_PERSISTENT)), optional(CACHES));
    }

    /**
     * Container for command arguments.
     */
    public static class Arguments {
        /** Caches. */
        private Set<String> caches;

        /** Exclude caches or groups. */
        private Set<String> excludeCaches;

        /** Calculate partition hash and print into standard output. */
        private boolean dump;

        /** Skip zeros partitions. */
        private boolean skipZeros;

        /** Check CRC sum on idle verify. */
        private boolean idleCheckCrc;

        /** Cache filter. */
        private CacheFilterEnum cacheFilterEnum;

        /**
         *
         */
        public Arguments(Set<String> caches, Set<String> excludeCaches, boolean dump, boolean skipZeros,
            boolean idleCheckCrc,
            CacheFilterEnum cacheFilterEnum) {
            this.caches = caches;
            this.excludeCaches = excludeCaches;
            this.dump = dump;
            this.skipZeros = skipZeros;
            this.idleCheckCrc = idleCheckCrc;
            this.cacheFilterEnum = cacheFilterEnum;
        }

        /**
         * @return Gets filter of caches, which will by checked.
         */
        public CacheFilterEnum getCacheFilterEnum() {
            return cacheFilterEnum;
        }

        /**
         * @return Caches.
         */
        public Set<String> caches() {
            return caches;
        }

        /**
         * @return Exclude caches or groups.
         */
        public Set<String> excludeCaches() {
            return excludeCaches;
        }

        /**
         * @return Calculate partition hash and print into standard output.
         */
        public boolean dump() {
            return dump;
        }

        /**
         * @return Check page CRC sum on idle verify flag.
         */
        public boolean idleCheckCrc() {
            return idleCheckCrc;
        }


        /**
         * @return Skip zeros partitions(size == 0) in result.
         */
        public boolean isSkipZeros() {
            return skipZeros;
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
        try (GridClient client = Command.startClient(clientCfg)) {
            Collection<GridClientNode> nodes = client.compute().nodes(GridClientNode::connectable);

            boolean idleVerifyV2 = true;

            for (GridClientNode node : nodes) {
                String nodeVerStr = node.attribute(IgniteNodeAttributes.ATTR_BUILD_VER);

                IgniteProductVersion nodeVer = IgniteProductVersion.fromString(nodeVerStr);

                if (nodeVer.compareTo(VerifyBackupPartitionsTaskV2.V2_SINCE_VER) < 0) {
                    idleVerifyV2 = false;

                    break;
                }
            }

            if (args.dump())
                cacheIdleVerifyDump(client, clientCfg, logger);
            else if (idleVerifyV2)
                cacheIdleVerifyV2(client, clientCfg);
            else
                legacyCacheIdleVerify(client, clientCfg, logger);
        }

        return null;
    }

    /** {@inheritDoc} */
    @Override public void parseArguments(CommandArgIterator argIter) {
        Set<String> cacheNames = null;
        boolean dump = false;
        boolean skipZeros = false;
        boolean idleCheckCrc = false;
        CacheFilterEnum cacheFilterEnum = CacheFilterEnum.DEFAULT;
        Set<String> excludeCaches = null;

        int idleVerifyArgsCnt = 5;

        while (argIter.hasNextSubArg() && idleVerifyArgsCnt-- > 0) {
            String nextArg = argIter.nextArg("");

            IdleVerifyCommandArg arg = CommandArgUtils.of(nextArg, IdleVerifyCommandArg.class);

            if (arg == null) {
                cacheNames = argIter.parseStringSet(nextArg);

                validateRegexes(cacheNames);
            }
            else {
                switch (arg) {
                    case DUMP:
                        dump = true;

                        break;

                    case SKIP_ZEROS:
                        skipZeros = true;

                        break;

                    case CHECK_CRC:
                        idleCheckCrc = true;

                        break;

                    case CACHE_FILTER:
                        String filter = argIter.nextArg("The cache filter should be specified. The following " +
                            "values can be used: " + Arrays.toString(CacheFilterEnum.values()) + '.');

                        cacheFilterEnum = CacheFilterEnum.valueOf(filter.toUpperCase());

                        break;

                    case EXCLUDE_CACHES:
                        excludeCaches = argIter.nextStringSet("caches, which will be excluded.");

                        validateRegexes(excludeCaches);

                        break;
                }
            }
        }

        if (idleCheckCrc) {
            if (cacheFilterEnum == ALL || cacheFilterEnum == SYSTEM) {
                throw new IllegalArgumentException(
                    IDLE_VERIFY + " with " + CHECK_CRC + " and " + CACHE_FILTER + " " + ALL + " or " + SYSTEM +
                        " not allowed. You should remove " + CHECK_CRC + " or change " + CACHE_FILTER + " value."
                );
            }

            if (F.constainsStringIgnoreCase(cacheNames, UTILITY_CACHE_NAME)) {
                throw new IllegalArgumentException(
                    IDLE_VERIFY + " with " + CHECK_CRC + " not allowed for `" + UTILITY_CACHE_NAME + "` cache."
                );
            }
        }

        args = new Arguments(cacheNames, excludeCaches, dump, skipZeros, idleCheckCrc, cacheFilterEnum);
    }

    /**
     * @param string To validate that given name is valed regex.
     */
    private void validateRegexes(Set<String> string) {
        string.forEach(s -> {
            try {
                Pattern.compile(s);
            }
            catch (PatternSyntaxException e) {
                throw new IgniteException(format("Invalid cache name regexp '%s': %s", s, e.getMessage()));
            }
        });
    }

    /**
     * @param client Client.
     * @param clientCfg Client configuration.
     */
    private void cacheIdleVerifyDump(
        GridClient client,
        GridClientConfiguration clientCfg,
        CommandLogger logger
    ) throws GridClientException {
        VisorIdleVerifyDumpTaskArg arg = new VisorIdleVerifyDumpTaskArg(
            args.caches(),
            args.excludeCaches(),
            args.isSkipZeros(),
            args.getCacheFilterEnum(),
            args.idleCheckCrc()
        );

        String path = executeTask(client, VisorIdleVerifyDumpTask.class, arg, clientCfg);

        logger.log("VisorIdleVerifyDumpTask successfully written output to '" + path + "'");
    }


    /**
     * @param client Client.
     * @param clientCfg Client configuration.
     */
    private void cacheIdleVerifyV2(
        GridClient client,
        GridClientConfiguration clientCfg
    ) throws GridClientException {
        IdleVerifyResultV2 res = executeTask(
            client,
            VisorIdleVerifyTaskV2.class,
            new VisorIdleVerifyTaskArg(args.caches(), args.excludeCaches(), args.idleCheckCrc()),
            clientCfg);

        res.print(System.out::print);
    }


    /**
     * @param client Client.
     * @param clientCfg Client configuration.
     */
    private void legacyCacheIdleVerify(
        GridClient client,
        GridClientConfiguration clientCfg,
        CommandLogger logger
    ) throws GridClientException {
        VisorIdleVerifyTaskResult res = executeTask(
            client,
            VisorIdleVerifyTask.class,
            new VisorIdleVerifyTaskArg(args.caches(), args.excludeCaches(), args.idleCheckCrc()),
            clientCfg);

        Map<PartitionKey, List<PartitionHashRecord>> conflicts = res.getConflicts();

        if (conflicts.isEmpty()) {
            logger.log("idle_verify check has finished, no conflicts have been found.");
            logger.nl();
        }
        else {
            logger.log("idle_verify check has finished, found " + conflicts.size() + " conflict partitions.");
            logger.nl();

            for (Map.Entry<PartitionKey, List<PartitionHashRecord>> entry : conflicts.entrySet()) {
                logger.log("Conflict partition: " + entry.getKey());

                logger.log("Partition instances: " + entry.getValue());
            }
        }
    }
}
