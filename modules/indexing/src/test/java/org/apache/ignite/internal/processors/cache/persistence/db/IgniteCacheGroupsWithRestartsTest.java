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

package org.apache.ignite.internal.processors.cache.persistence.db;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiFunction;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.cache.QueryEntity;
import org.apache.ignite.cache.QueryIndex;
import org.apache.ignite.cache.QueryIndexType;
import org.apache.ignite.cache.affinity.rendezvous.RendezvousAffinityFunction;
import org.apache.ignite.configuration.CacheConfiguration;
import org.apache.ignite.configuration.ConnectorConfiguration;
import org.apache.ignite.configuration.DataRegionConfiguration;
import org.apache.ignite.configuration.DataStorageConfiguration;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.internal.IgniteEx;
import org.apache.ignite.internal.commandline.CommandHandler;
import org.apache.ignite.internal.commandline.cache.argument.FindAndDeleteGarbageArg;
import org.apache.ignite.internal.util.typedef.internal.CU;
import org.apache.ignite.internal.util.typedef.internal.S;
import org.apache.ignite.internal.visor.VisorTaskArgument;
import org.apache.ignite.internal.visor.cache.VisorFindAndDeleteGarbageInPersistenceJobResult;
import org.apache.ignite.internal.visor.cache.VisorFindAndDeleteGarbageInPersistenceTask;
import org.apache.ignite.internal.visor.cache.VisorFindAndDeleteGarbageInPersistenceTaskArg;
import org.apache.ignite.internal.visor.cache.VisorFindAndDeleteGarbageInPersistenceTaskResult;
import org.apache.ignite.testframework.junits.WithSystemProperty;
import org.apache.ignite.testframework.junits.common.GridCommonAbstractTest;
import org.apache.ignite.testframework.junits.multijvm.IgniteProcessProxy;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import static org.apache.ignite.internal.processors.cache.persistence.GridCacheDatabaseSharedManager.IGNITE_PDS_SKIP_CHECKPOINT_ON_NODE_STOP;

/**
 * Testing corner cases in cache group functionality:
 * -stopping cache in shared group and immediate node leaving;
 * -starting cache in shared group with the same name as destroyed one;
 * -etc.
 */
@WithSystemProperty(key=IGNITE_PDS_SKIP_CHECKPOINT_ON_NODE_STOP, value="true")
@SuppressWarnings({"unchecked", "ThrowableNotThrown"})
public class IgniteCacheGroupsWithRestartsTest extends GridCommonAbstractTest {
    /** Group name. */
    public static final String GROUP = "group";

    /** {@inheritDoc} */
    @Override protected IgniteConfiguration getConfiguration(String gridName) throws Exception {
        IgniteConfiguration configuration = super.getConfiguration(gridName);

        configuration.setConnectorConfiguration(new ConnectorConfiguration());

        DataStorageConfiguration cfg = new DataStorageConfiguration();

        cfg.setDefaultDataRegionConfiguration(new DataRegionConfiguration()
            .setPersistenceEnabled(true)
            .setMaxSize(256 * 1024 * 1024));

        configuration.setDataStorageConfiguration(cfg);

        return configuration;
    }

    /**
     * @param i Cache index number.
     * @return Cache configuration with the given number in name.
     */
    private CacheConfiguration<Object, Object> getCacheConfiguration(int i) {
        CacheConfiguration ccfg = new CacheConfiguration();

        LinkedHashMap<String, String> fields = new LinkedHashMap<>();

        fields.put("updateDate", "java.lang.Date");
        fields.put("amount", "java.lang.Long");
        fields.put("name", "java.lang.String");

        Set<QueryIndex> indices = Collections.singleton(new QueryIndex("name", QueryIndexType.SORTED));

        ccfg.setName(getCacheName(i))
            .setGroupName("group")
            .setQueryEntities(Collections.singletonList(
                new QueryEntity(Long.class, Account.class)
                    .setFields(fields)
                    .setIndexes(indices)
            ))
            .setAffinity(new RendezvousAffinityFunction(false, 64));

        return ccfg;
    }

    /**
     * @param i Index.
     * @return Generated cache name for index.
     */
    private String getCacheName(int i) {
        return "cache-" + i;
    }

    /** {@inheritDoc} */
    @Override protected void beforeTest() throws Exception {
        afterTest();
    }

    /** {@inheritDoc} */
    @Override protected void afterTest() throws Exception {
        super.afterTest();

        stopAllGrids();

        cleanPersistenceDir();
    }

    /**
     * @throws Exception If failed.
     */
    @Ignore("https://issues.apache.org/jira/browse/IGNITE-8717")
    @Test
    public void testNodeRestartRightAfterCacheStop() throws Exception {
        IgniteEx ex = startGrids(3);

        prepareCachesAndData(ex);

        ex.destroyCache(getCacheName(0));

        assertNull(ex.cachex(getCacheName(0)));

        IgniteProcessProxy.kill(grid(2).configuration().getIgniteInstanceName());

        startGrid(2);

        assertNull(ex.cachex(getCacheName(0)));

        IgniteCache<Object, Object> cache = ex.createCache(getCacheConfiguration(0));

        awaitPartitionMapExchange();

        assertEquals(0, cache.size());
    }

    /**
     * @throws Exception If failed.
     */
    @Test
    public void testCleaningGarbageAfterCacheDestroyedAndNodeStop() throws Exception {
        testFindAndDeleteGarbage(this::executeTask);
    }


    /**
     * @throws Exception If failed.
     */
    @Test
    public void testCleaningGarbageAfterCacheDestroyedAndNodeStop_ControlConsoleUtil() throws Exception {
        testFindAndDeleteGarbage(this::executeTaskViaControlConsoleUtil);
    }

    /**
     * @param doFindAndRemove Do find and remove.
     */
    private void testFindAndDeleteGarbage(
        BiFunction<IgniteEx, Boolean, VisorFindAndDeleteGarbageInPersistenceTaskResult> doFindAndRemove
    ) throws Exception {
        IgniteEx ignite = startGrids(3);

        prepareCachesAndData(ignite);

        ignite.destroyCache(getCacheName(0));

        assertNull(ignite.cachex(getCacheName(0)));

        Thread.sleep(5_000); // waiting for cache.dat deletion

        stopGrid(2, true);

        IgniteEx ex1 = startGrid(2);

        assertNull(ignite.cachex(getCacheName(0)));

        awaitPartitionMapExchange();

        VisorFindAndDeleteGarbageInPersistenceTaskResult taskResult = doFindAndRemove.apply(ex1, false);

        VisorFindAndDeleteGarbageInPersistenceJobResult result = taskResult.result().get(ex1.localNode().id());

        Assert.assertTrue(result.hasGarbage());

        Assert.assertTrue(result.checkResult()
            .get(CU.cacheId("group"))
            .get(CU.cacheId(getCacheName(0))) > 0);

        //removing garbage
        result = doFindAndRemove.apply(ex1, true).result().get(ex1.localNode().id());

        Assert.assertTrue(result.hasGarbage());

        result = doFindAndRemove.apply(ex1, false).result().get(ex1.localNode().id());

        Assert.assertFalse(result.hasGarbage());
    }

    /**
     * @param ignite Ignite to execute task on.
     * @param deleteFoundGarbage If clearing mode should be used.
     * @return Result of task run.
     */
    private VisorFindAndDeleteGarbageInPersistenceTaskResult executeTask(
        IgniteEx ignite,
        boolean deleteFoundGarbage
    ) {
        VisorFindAndDeleteGarbageInPersistenceTaskArg group = new VisorFindAndDeleteGarbageInPersistenceTaskArg(
            Collections.singleton(GROUP), deleteFoundGarbage, null);

        UUID id = ignite.localNode().id();

        VisorTaskArgument arg = new VisorTaskArgument(id, group, true);

        VisorFindAndDeleteGarbageInPersistenceTaskResult result =
            ignite.compute().execute(VisorFindAndDeleteGarbageInPersistenceTask.class, arg);

        return result;
    }

    /**
     * @param ignite Ignite to execute task on.
     * @param deleteFoundGarbage If clearing mode should be used.
     * @return Result of task run.
     */
    private VisorFindAndDeleteGarbageInPersistenceTaskResult executeTaskViaControlConsoleUtil(
        IgniteEx ignite,
        boolean deleteFoundGarbage
    ) {
        CommandHandler handler = new CommandHandler();

        List<String> args = new ArrayList<>(Arrays.asList("--yes", "--port", "11212", "--cache", "find_garbage",
            ignite.localNode().id().toString()));

        if (deleteFoundGarbage)
            args.add(FindAndDeleteGarbageArg.DELETE.argName());

        handler.execute(args);

        return handler.getLastOperationResult();
    }

    /**
     * @param ignite Ignite instance.
     */
    private void prepareCachesAndData(IgniteEx ignite) {
        ignite.cluster().active(true);

        for (int j = 0; j < 3; j++) {
            for (int i = 0; i < 64 * 10; i++) {
                IgniteCache<Object, Object> cache = ignite.getOrCreateCache(getCacheConfiguration(j));

                byte[] val = new byte[ThreadLocalRandom.current().nextInt(8148)];

                Arrays.fill(val, (byte)i);

                cache.put((long)i, new Account(i));
            }
        }
    }

    /**
     *
     */
    static class Account {
        /** */
        private final int val;

        /**
         * @param val Value.
         */
        public Account(int val) {
            this.val = val;
        }

        /**
         * @return Value.
         */
        public int value() {
            return val;
        }

        /** {@inheritDoc} */
        @Override public String toString() {
            return S.toString(Account.class, this);
        }
    }
}
