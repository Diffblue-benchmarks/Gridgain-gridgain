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

package org.apache.ignite.internal.processors.hadoop.impl.delegate;

import org.apache.hadoop.fs.FileSystem;
import org.apache.ignite.IgniteException;
import org.apache.ignite.hadoop.fs.HadoopFileSystemFactory;
import org.apache.ignite.internal.processors.hadoop.delegate.HadoopFileSystemFactoryDelegate;
import org.apache.ignite.lifecycle.LifecycleAware;

import java.io.IOException;

/**
 * Hadoop file system factory delegate for non-standard factories.
 */
public class HadoopDefaultFileSystemFactoryDelegate implements HadoopFileSystemFactoryDelegate {
    /** Factory. */
    private final HadoopFileSystemFactory factory;

    /**
     * Constructor.
     *
     * @param factory Factory.
     */
    public HadoopDefaultFileSystemFactoryDelegate(HadoopFileSystemFactory factory) {
        assert factory != null;

        this.factory = factory;
    }

    /** {@inheritDoc} */
    @Override public FileSystem get(String usrName) throws IOException {
        return (FileSystem)factory.get(usrName);
    }

    /** {@inheritDoc} */
    @Override public void start() throws IgniteException {
        if (factory instanceof LifecycleAware)
            ((LifecycleAware)factory).start();
    }

    /** {@inheritDoc} */
    @Override public void stop() throws IgniteException {
        if (factory instanceof LifecycleAware)
            ((LifecycleAware)factory).stop();
    }
}
