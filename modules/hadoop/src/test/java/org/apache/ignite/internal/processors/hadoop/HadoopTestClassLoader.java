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

package org.apache.ignite.internal.processors.hadoop;

import org.apache.ignite.IgniteException;
import org.apache.ignite.internal.util.IgniteUtils;
import org.apache.ignite.internal.util.typedef.F;
import org.apache.ignite.internal.util.typedef.X;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Hadoop test class loader aimed to provide better isolation.
 */
public class HadoopTestClassLoader extends URLClassLoader {
    /** Parent class loader. */
   private static final ClassLoader APP_CLS_LDR = HadoopTestClassLoader.class.getClassLoader();

    /** */
    private static final Collection<URL> APP_JARS = F.asList(IgniteUtils.classLoaderUrls(APP_CLS_LDR));

    /** All participating URLs. */
    private static final URL[] URLS;

    static {
        try {
            List<URL> res = new ArrayList<>();

            for (URL url : APP_JARS) {
                String urlStr = url.toString();

                if (urlStr.contains("modules/hadoop/"))
                    res.add(url);
            }

            res.addAll(HadoopClasspathUtils.classpathForClassLoader());

            X.println(">>> " + HadoopTestClassLoader.class.getSimpleName() + " static paths:");

            for (URL url : res)
                X.println(">>> \t" + url.toString());

            URLS = res.toArray(new URL[res.size()]);
        }
        catch (Exception e) {
            throw new IgniteException("Failed to initialize class loader JARs.", e);
        }
    }

    /**
     * Constructor.
     */
    public HadoopTestClassLoader() {
        super(URLS, APP_CLS_LDR);
    }

    /** {@inheritDoc} */
    @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        if (HadoopClassLoader.loadByCurrentClassloader(name)) {
            try {
                synchronized (getClassLoadingLock(name)) {
                    // First, check if the class has already been loaded
                    Class c = findLoadedClass(name);

                    if (c == null)
                        c = findClass(name);

                    if (resolve)
                        resolveClass(c);

                    return c;
                }
            }
            catch (NoClassDefFoundError | ClassNotFoundException e) {
                throw new IgniteException("Failed to load class by test class loader: " + name, e);
            }
        }

        return super.loadClass(name, resolve);
    }
}
