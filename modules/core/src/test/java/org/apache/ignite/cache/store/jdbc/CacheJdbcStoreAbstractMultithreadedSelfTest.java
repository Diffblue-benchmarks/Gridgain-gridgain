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

package org.apache.ignite.cache.store.jdbc;

import java.net.MalformedURLException;
import java.net.URL;
import java.sql.Connection;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Callable;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.IgniteCheckedException;
import org.apache.ignite.cache.store.jdbc.model.Gender;
import org.apache.ignite.cache.store.jdbc.model.Organization;
import org.apache.ignite.cache.store.jdbc.model.OrganizationKey;
import org.apache.ignite.cache.store.jdbc.model.Person;
import org.apache.ignite.cache.store.jdbc.model.PersonKey;
import org.apache.ignite.configuration.CacheConfiguration;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.internal.IgniteInternalFuture;
import org.apache.ignite.internal.util.typedef.X;
import org.apache.ignite.internal.util.typedef.internal.U;
import org.apache.ignite.testframework.junits.common.GridCommonAbstractTest;
import org.apache.ignite.transactions.Transaction;
import org.jetbrains.annotations.Nullable;
import org.junit.Test;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.xml.XmlBeanDefinitionReader;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.io.UrlResource;

import static org.apache.ignite.cache.CacheAtomicityMode.ATOMIC;
import static org.apache.ignite.cache.CacheMode.PARTITIONED;
import static org.apache.ignite.testframework.GridTestUtils.runMultiThreaded;
import static org.apache.ignite.testframework.GridTestUtils.runMultiThreadedAsync;

/**
 *
 */
public abstract class CacheJdbcStoreAbstractMultithreadedSelfTest<T extends CacheAbstractJdbcStore>
    extends GridCommonAbstractTest {
    /** Default config with mapping. */
    private static final String DFLT_MAPPING_CONFIG = "modules/core/src/test/config/store/jdbc/ignite-jdbc-type.xml";

    /** Database connection URL. */
    protected static final String DFLT_CONN_URL = "jdbc:h2:mem:autoCacheStore;DB_CLOSE_DELAY=-1";

    /** Number of transactions. */
    private static final int TX_CNT = 200;

    /** Number of transactions. */
    private static final int BATCH_CNT = 2000;

    /** Cache store. */
    protected static CacheAbstractJdbcStore store;

    /** {@inheritDoc} */
    @Override protected void beforeTestsStarted() throws Exception {
        store = store();

        URL cfgUrl;

        try {
            cfgUrl = new URL(DFLT_MAPPING_CONFIG);
        }
        catch (MalformedURLException ignore) {
            cfgUrl = U.resolveIgniteUrl(DFLT_MAPPING_CONFIG);
        }

        if (cfgUrl == null)
            throw new Exception("Failed to resolve metadata path: " + DFLT_MAPPING_CONFIG);

        try {
            GenericApplicationContext springCtx = new GenericApplicationContext();

            new XmlBeanDefinitionReader(springCtx).loadBeanDefinitions(new UrlResource(cfgUrl));

            springCtx.refresh();

            Collection<JdbcType> types = new ArrayList<>(springCtx.getBeansOfType(JdbcType.class).values());

            store.setTypes(types.toArray(new JdbcType[types.size()]));
        }
        catch (BeansException e) {
            if (X.hasCause(e, ClassNotFoundException.class))
                throw new IgniteCheckedException("Failed to instantiate Spring XML application context " +
                    "(make sure all classes used in Spring configuration are present at CLASSPATH) " +
                    "[springUrl=" + cfgUrl + ']', e);
            else
                throw new IgniteCheckedException("Failed to instantiate Spring XML application context [springUrl=" +
                    cfgUrl + ", err=" + e.getMessage() + ']', e);
        }

    }

    /** {@inheritDoc} */
    @Override protected void afterTestsStopped() throws Exception {
        store = null;
    }

    /** {@inheritDoc} */
    @Override protected void beforeTest() throws Exception {
        Connection conn = DriverManager.getConnection(DFLT_CONN_URL, "sa", "");

        Statement stmt = conn.createStatement();

        stmt.executeUpdate("DROP TABLE IF EXISTS Organization");
        stmt.executeUpdate("DROP TABLE IF EXISTS Person");

        stmt.executeUpdate("CREATE TABLE Organization (id integer PRIMARY KEY, name varchar(50), city varchar(50))");
        stmt.executeUpdate("CREATE TABLE Person (id integer PRIMARY KEY, org_id integer, name varchar(50))");

        conn.commit();

        U.closeQuiet(stmt);

        U.closeQuiet(conn);

        startGrid();
    }

    /** {@inheritDoc} */
    @Override protected void afterTest() throws Exception {
        stopAllGrids();
    }

    /**
     * @return New store.
     * @throws Exception In case of error.
     */
    protected abstract T store() throws Exception;

    /** {@inheritDoc} */
    @Override protected IgniteConfiguration getConfiguration(String igniteInstanceName) throws Exception {
        IgniteConfiguration c = super.getConfiguration(igniteInstanceName);

        c.setCacheConfiguration(cacheConfiguration());

        return c;
    }

    /**
     * @return Cache configuration.
     * @throws Exception If failed.
     */
    protected CacheConfiguration cacheConfiguration() throws Exception {
        CacheConfiguration cc = defaultCacheConfiguration();

        cc.setCacheMode(PARTITIONED);
        cc.setAtomicityMode(ATOMIC);
        cc.setWriteBehindEnabled(false);

        cc.setCacheStoreFactory(singletonFactory(store));

        cc.setReadThrough(true);
        cc.setWriteThrough(true);
        cc.setLoadPreviousValue(true);

        return cc;
    }

    /**
     * @throws Exception If failed.
     */
    @Test
    public void testMultithreadedPut() throws Exception {
        IgniteInternalFuture<?> fut1 = runMultiThreadedAsync(new Callable<Object>() {
            private final Random rnd = new Random();

            @Nullable @Override public Object call() throws Exception {
                for (int i = 0; i < TX_CNT; i++) {
                    IgniteCache<Object, Object> cache = jcache();

                    int id = rnd.nextInt(1000);

                    if (rnd.nextBoolean())
                        cache.put(new OrganizationKey(id), new Organization(id, "Name" + id, "City" + id));
                    else
                        cache.put(new PersonKey(id), new Person(id, rnd.nextInt(),
                            new Date(System.currentTimeMillis()), "Name" + id, 1, Gender.random()));
                }

                return null;
            }
        }, 4, "put");

        IgniteInternalFuture<?> fut2 = runMultiThreadedAsync(new Callable<Object>() {
            private final Random rnd = new Random();

            @Nullable @Override public Object call() throws Exception {
                for (int i = 0; i < TX_CNT; i++) {
                    IgniteCache<Object, Object> cache = jcache();

                    int id = rnd.nextInt(1000);

                    if (rnd.nextBoolean())
                        cache.putIfAbsent(new OrganizationKey(id), new Organization(id, "Name" + id, "City" + id));
                    else
                        cache.putIfAbsent(new PersonKey(id), new Person(id, rnd.nextInt(),
                            new Date(System.currentTimeMillis()), "Name" + id, i, Gender.random()));
                }

                return null;
            }
        }, 8, "putIfAbsent");

        fut1.get();
        fut2.get();
    }

    /**
     * @throws Exception If failed.
     */
    @Test
    public void testMultithreadedPutAll() throws Exception {
        multithreaded(new Callable<Object>() {
            private final Random rnd = new Random();

            @Nullable @Override public Object call() throws Exception {
                for (int i = 0; i < TX_CNT; i++) {
                    int cnt = rnd.nextInt(BATCH_CNT);

                    List<Integer> ids = new ArrayList<>(cnt);

                    for (int j = 0; j < cnt; j++) {
                        int id = rnd.nextInt(5000);

                        if (!ids.contains(id))
                            ids.add(id);
                    }

                    Collections.sort(ids);

                    Map<Object, Object> map = U.newLinkedHashMap(cnt);

                    for (Integer id : ids) {
                        if (rnd.nextBoolean())
                            map.put(new OrganizationKey(id), new Organization(id, "Name" + id, "City" + id));
                        else
                            map.put(new PersonKey(id), new Person(id, rnd.nextInt(),
                                new Date(System.currentTimeMillis()), "Name" + id, 1, Gender.random()));
                    }

                    IgniteCache<Object, Object> cache = jcache();

                    cache.putAll(map);
                }

                return null;
            }
        }, 8, "putAll");
    }

    /**
     * @throws Exception If failed.
     */
    @Test
    public void testMultithreadedExplicitTx() throws Exception {
        runMultiThreaded(new Callable<Object>() {
            private final Random rnd = new Random();

            @Nullable @Override public Object call() throws Exception {
                for (int i = 0; i < TX_CNT; i++) {
                    IgniteCache<PersonKey, Person> cache = jcache();

                    try (Transaction tx = grid().transactions().txStart()) {
                        cache.put(new PersonKey(1), new Person(1, rnd.nextInt(),
                            new Date(System.currentTimeMillis()), "Name" + 1, 1, Gender.random()));
                        cache.put(new PersonKey(2), new Person(2, rnd.nextInt(),
                            new Date(System.currentTimeMillis()), "Name" + 2, 2, Gender.random()));
                        cache.put(new PersonKey(3), new Person(3, rnd.nextInt(),
                            new Date(System.currentTimeMillis()), "Name" + 3, 3, Gender.random()));

                        cache.get(new PersonKey(1));
                        cache.get(new PersonKey(4));

                        Map<PersonKey, Person> map =  U.newHashMap(2);

                        map.put(new PersonKey(5), new Person(5, rnd.nextInt(),
                            new Date(System.currentTimeMillis()), "Name" + 5, 5, Gender.random()));
                        map.put(new PersonKey(6), new Person(6, rnd.nextInt(),
                            new Date(System.currentTimeMillis()), "Name" + 6, 6, Gender.random()));

                        cache.putAll(map);

                        tx.commit();
                    }
                }

                return null;
            }
        }, 8, "tx");
    }
}
