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

package org.apache.ignite.testsuites;

import java.util.ArrayList;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.ignite.IgniteSystemProperties;
import org.apache.ignite.internal.processors.hadoop.HadoopTestClassLoader;
import org.apache.ignite.internal.processors.hadoop.impl.HadoopTeraSortTest;
import org.apache.ignite.internal.processors.hadoop.impl.client.HadoopClientProtocolEmbeddedSelfTest;
import org.apache.ignite.internal.processors.hadoop.impl.client.HadoopClientProtocolMultipleServersSelfTest;
import org.apache.ignite.internal.processors.hadoop.impl.client.HadoopClientProtocolSelfTest;
import org.apache.ignite.internal.processors.hadoop.impl.HadoopTxConfigCacheTest;
import org.apache.ignite.internal.processors.hadoop.impl.fs.KerberosHadoopFileSystemFactorySelfTest;
import org.apache.ignite.internal.processors.hadoop.impl.igfs.Hadoop1OverIgfsProxyTest;
import org.apache.ignite.internal.processors.hadoop.impl.igfs.IgniteHadoopFileSystemClientBasedDualAsyncSelfTest;
import org.apache.ignite.internal.processors.hadoop.impl.igfs.IgniteHadoopFileSystemClientBasedDualSyncSelfTest;
import org.apache.ignite.internal.processors.hadoop.impl.igfs.IgniteHadoopFileSystemClientBasedOpenTest;
import org.apache.ignite.internal.processors.hadoop.impl.igfs.IgniteHadoopFileSystemClientBasedPrimarySelfTest;
import org.apache.ignite.internal.processors.hadoop.impl.igfs.IgniteHadoopFileSystemClientBasedProxySelfTest;
import org.apache.ignite.internal.processors.hadoop.impl.igfs.IgniteHadoopFileSystemLoopbackExternalToClientDualAsyncSelfTest;
import org.apache.ignite.internal.processors.hadoop.impl.igfs.IgniteHadoopFileSystemLoopbackExternalToClientDualSyncSelfTest;
import org.apache.ignite.internal.processors.hadoop.impl.igfs.IgniteHadoopFileSystemLoopbackExternalToClientPrimarySelfTest;
import org.apache.ignite.internal.processors.hadoop.impl.igfs.IgniteHadoopFileSystemLoopbackExternalToClientProxySelfTest;
import org.apache.ignite.internal.processors.hadoop.impl.taskexecutor.HadoopExecutorServiceTest;
import org.apache.ignite.internal.processors.hadoop.impl.util.BasicUserNameMapperSelfTest;
import org.apache.ignite.internal.processors.hadoop.impl.util.ChainedUserNameMapperSelfTest;
import org.apache.ignite.internal.processors.hadoop.impl.util.KerberosUserNameMapperSelfTest;
import org.apache.ignite.internal.processors.hadoop.impl.igfs.Hadoop1OverIgfsDualAsyncTest;
import org.apache.ignite.internal.processors.hadoop.impl.igfs.Hadoop1OverIgfsDualSyncTest;
import org.apache.ignite.internal.processors.hadoop.impl.igfs.HadoopFIleSystemFactorySelfTest;
import org.apache.ignite.internal.processors.hadoop.impl.igfs.HadoopIgfs20FileSystemLoopbackPrimarySelfTest;
import org.apache.ignite.internal.processors.hadoop.impl.igfs.HadoopIgfsDualAsyncSelfTest;
import org.apache.ignite.internal.processors.hadoop.impl.igfs.HadoopIgfsDualSyncSelfTest;
import org.apache.ignite.internal.processors.hadoop.impl.igfs.HadoopSecondaryFileSystemConfigurationTest;
import org.apache.ignite.internal.processors.hadoop.impl.igfs.IgfsEventsTestSuite;
import org.apache.ignite.internal.processors.hadoop.impl.igfs.IgniteHadoopFileSystemClientSelfTest;
import org.apache.ignite.internal.processors.hadoop.impl.igfs.IgniteHadoopFileSystemHandshakeSelfTest;
import org.apache.ignite.internal.processors.hadoop.impl.igfs.IgniteHadoopFileSystemLoggerSelfTest;
import org.apache.ignite.internal.processors.hadoop.impl.igfs.IgniteHadoopFileSystemLoggerStateSelfTest;
import org.apache.ignite.internal.processors.hadoop.impl.igfs.IgniteHadoopFileSystemLoopbackEmbeddedDualAsyncSelfTest;
import org.apache.ignite.internal.processors.hadoop.impl.igfs.IgniteHadoopFileSystemLoopbackEmbeddedDualSyncSelfTest;
import org.apache.ignite.internal.processors.hadoop.impl.igfs.IgniteHadoopFileSystemLoopbackEmbeddedPrimarySelfTest;
import org.apache.ignite.internal.processors.hadoop.impl.igfs.IgniteHadoopFileSystemLoopbackEmbeddedSecondarySelfTest;
import org.apache.ignite.internal.processors.hadoop.impl.igfs.IgniteHadoopFileSystemLoopbackExternalDualAsyncSelfTest;
import org.apache.ignite.internal.processors.hadoop.impl.igfs.IgniteHadoopFileSystemLoopbackExternalDualSyncSelfTest;
import org.apache.ignite.internal.processors.hadoop.impl.igfs.IgniteHadoopFileSystemLoopbackExternalPrimarySelfTest;
import org.apache.ignite.internal.processors.hadoop.impl.igfs.IgniteHadoopFileSystemLoopbackExternalSecondarySelfTest;
import org.apache.ignite.internal.processors.hadoop.impl.HadoopCommandLineTest;
import org.apache.ignite.internal.processors.hadoop.impl.HadoopFileSystemsTest;
import org.apache.ignite.internal.processors.hadoop.impl.HadoopGroupingTest;
import org.apache.ignite.internal.processors.hadoop.impl.HadoopJobTrackerSelfTest;
import org.apache.ignite.internal.processors.hadoop.impl.HadoopMapReduceEmbeddedSelfTest;
import org.apache.ignite.internal.processors.hadoop.impl.HadoopMapReduceErrorResilienceTest;
import org.apache.ignite.internal.processors.hadoop.impl.HadoopMapReduceTest;
import org.apache.ignite.internal.processors.hadoop.impl.HadoopNoHadoopMapReduceTest;
import org.apache.ignite.internal.processors.hadoop.impl.HadoopSerializationWrapperSelfTest;
import org.apache.ignite.internal.processors.hadoop.impl.HadoopSnappyFullMapReduceTest;
import org.apache.ignite.internal.processors.hadoop.impl.HadoopSnappyTest;
import org.apache.ignite.internal.processors.hadoop.impl.HadoopSortingTest;
import org.apache.ignite.internal.processors.hadoop.impl.HadoopSplitWrapperSelfTest;
import org.apache.ignite.internal.processors.hadoop.impl.HadoopTaskExecutionSelfTest;
import org.apache.ignite.internal.processors.hadoop.impl.HadoopTasksV1Test;
import org.apache.ignite.internal.processors.hadoop.impl.HadoopTasksV2Test;
import org.apache.ignite.internal.processors.hadoop.impl.HadoopUserLibsSelfTest;
import org.apache.ignite.internal.processors.hadoop.impl.HadoopV2JobSelfTest;
import org.apache.ignite.internal.processors.hadoop.impl.HadoopValidationSelfTest;
import org.apache.ignite.internal.processors.hadoop.impl.HadoopWeightedMapReducePlannerTest;
import org.apache.ignite.internal.processors.hadoop.impl.HadoopWeightedPlannerMapReduceTest;
import org.apache.ignite.internal.processors.hadoop.impl.shuffle.collections.HadoopConcurrentHashMultimapSelftest;
import org.apache.ignite.internal.processors.hadoop.impl.shuffle.collections.HadoopHashMapSelfTest;
import org.apache.ignite.internal.processors.hadoop.impl.shuffle.collections.HadoopSkipListSelfTest;
import org.apache.ignite.internal.processors.hadoop.impl.shuffle.streams.HadoopDataStreamSelfTest;
import org.apache.ignite.internal.util.typedef.F;
import org.apache.ignite.internal.util.typedef.X;
import org.apache.ignite.internal.util.typedef.internal.U;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.apache.ignite.testframework.junits.DynamicSuite;
import org.junit.runner.RunWith;

import static org.apache.ignite.testframework.GridTestUtils.modeToPermissionSet;

/**
 * Test suite for Hadoop Map Reduce engine.
 */
@RunWith(DynamicSuite.class)
public class IgniteHadoopTestSuite {
    /**
     * @return Test suite.
     * @throws Exception Thrown in case of the failure.
     */
    public static List<Class<?>> suite() throws Exception {
        downloadHadoop();
        downloadHive();

        final ClassLoader ldr = new HadoopTestClassLoader();

        List<Class<?>> suite = new ArrayList<>();

        suite.add(ldr.loadClass(HadoopUserLibsSelfTest.class.getName()));

        suite.add(ldr.loadClass(HadoopWeightedMapReducePlannerTest.class.getName()));

        suite.add(ldr.loadClass(BasicUserNameMapperSelfTest.class.getName()));
        suite.add(ldr.loadClass(KerberosUserNameMapperSelfTest.class.getName()));
        suite.add(ldr.loadClass(ChainedUserNameMapperSelfTest.class.getName()));

        suite.add(ldr.loadClass(KerberosHadoopFileSystemFactorySelfTest.class.getName()));

        suite.add(ldr.loadClass(HadoopTeraSortTest.class.getName()));

        suite.add(ldr.loadClass(HadoopSnappyTest.class.getName()));
        suite.add(ldr.loadClass(HadoopSnappyFullMapReduceTest.class.getName()));

        suite.add(ldr.loadClass(HadoopIgfs20FileSystemLoopbackPrimarySelfTest.class.getName()));

        suite.add(ldr.loadClass(HadoopIgfsDualSyncSelfTest.class.getName()));
        suite.add(ldr.loadClass(HadoopIgfsDualAsyncSelfTest.class.getName()));

        suite.add(ldr.loadClass(Hadoop1OverIgfsDualSyncTest.class.getName()));
        suite.add(ldr.loadClass(Hadoop1OverIgfsDualAsyncTest.class.getName()));
        suite.add(ldr.loadClass(Hadoop1OverIgfsProxyTest.class.getName()));

        suite.add(ldr.loadClass(HadoopFIleSystemFactorySelfTest.class.getName()));

        suite.add(ldr.loadClass(IgniteHadoopFileSystemLoopbackExternalPrimarySelfTest.class.getName()));
        suite.add(ldr.loadClass(IgniteHadoopFileSystemLoopbackExternalSecondarySelfTest.class.getName()));
        suite.add(ldr.loadClass(IgniteHadoopFileSystemLoopbackExternalDualSyncSelfTest.class.getName()));
        suite.add(ldr.loadClass(IgniteHadoopFileSystemLoopbackExternalDualAsyncSelfTest.class.getName()));
        suite.add(ldr.loadClass(IgniteHadoopFileSystemLoopbackEmbeddedPrimarySelfTest.class.getName()));
        suite.add(ldr.loadClass(IgniteHadoopFileSystemLoopbackEmbeddedSecondarySelfTest.class.getName()));
        suite.add(ldr.loadClass(IgniteHadoopFileSystemLoopbackEmbeddedDualSyncSelfTest.class.getName()));
        suite.add(ldr.loadClass(IgniteHadoopFileSystemLoopbackEmbeddedDualAsyncSelfTest.class.getName()));
        suite.add(ldr.loadClass(IgniteHadoopFileSystemClientBasedPrimarySelfTest.class.getName()));
        suite.add(ldr.loadClass(IgniteHadoopFileSystemClientBasedDualAsyncSelfTest.class.getName()));
        suite.add(ldr.loadClass(IgniteHadoopFileSystemClientBasedDualSyncSelfTest.class.getName()));
        suite.add(ldr.loadClass(IgniteHadoopFileSystemClientBasedProxySelfTest.class.getName()));
        suite.add(ldr.loadClass(IgniteHadoopFileSystemLoopbackExternalToClientPrimarySelfTest.class.getName()));
        suite.add(ldr.loadClass(IgniteHadoopFileSystemLoopbackExternalToClientDualSyncSelfTest.class.getName()));
        suite.add(ldr.loadClass(IgniteHadoopFileSystemLoopbackExternalToClientDualAsyncSelfTest.class.getName()));
        suite.add(ldr.loadClass(IgniteHadoopFileSystemLoopbackExternalToClientProxySelfTest.class.getName()));

        suite.add(ldr.loadClass(IgniteHadoopFileSystemClientSelfTest.class.getName()));

        suite.add(ldr.loadClass(IgniteHadoopFileSystemLoggerStateSelfTest.class.getName()));
        suite.add(ldr.loadClass(IgniteHadoopFileSystemLoggerSelfTest.class.getName()));

        suite.add(ldr.loadClass(IgniteHadoopFileSystemHandshakeSelfTest.class.getName()));

        suite.add(IgfsEventsTestSuite.IgfsEventsNoarchOnlyTest.class);

        suite.add(ldr.loadClass(HadoopFileSystemsTest.class.getName()));

        suite.add(ldr.loadClass(HadoopExecutorServiceTest.class.getName()));

        suite.add(ldr.loadClass(HadoopValidationSelfTest.class.getName()));

        suite.add(ldr.loadClass(HadoopJobTrackerSelfTest.class.getName()));

        suite.add(ldr.loadClass(HadoopHashMapSelfTest.class.getName()));
        suite.add(ldr.loadClass(HadoopDataStreamSelfTest.class.getName()));
        suite.add(ldr.loadClass(HadoopConcurrentHashMultimapSelftest.class.getName()));

        suite.add(ldr.loadClass(HadoopSkipListSelfTest.class.getName()));

        suite.add(ldr.loadClass(HadoopTaskExecutionSelfTest.class.getName()));

        suite.add(ldr.loadClass(HadoopV2JobSelfTest.class.getName()));

        suite.add(ldr.loadClass(HadoopSerializationWrapperSelfTest.class.getName()));
        suite.add(ldr.loadClass(HadoopSplitWrapperSelfTest.class.getName()));

        suite.add(ldr.loadClass(HadoopTasksV1Test.class.getName()));
        suite.add(ldr.loadClass(HadoopTasksV2Test.class.getName()));

        suite.add(ldr.loadClass(HadoopMapReduceTest.class.getName()));
        suite.add(ldr.loadClass(HadoopWeightedPlannerMapReduceTest.class.getName()));
        suite.add(ldr.loadClass(HadoopNoHadoopMapReduceTest.class.getName()));
        suite.add(ldr.loadClass(HadoopMapReduceErrorResilienceTest.class.getName()));

        suite.add(ldr.loadClass(HadoopMapReduceEmbeddedSelfTest.class.getName()));

        suite.add(ldr.loadClass(HadoopSortingTest.class.getName()));

        // TODO https://issues.apache.org/jira/browse/IGNITE-3167
//        suite.add(ldr.loadClass(HadoopExternalTaskExecutionSelfTest.class.getName()));
//        suite.add(ldr.loadClass(HadoopExternalCommunicationSelfTest.class.getName()));
//        suite.add(ldr.loadClass(HadoopSortingExternalTest.class.getName()));

        suite.add(ldr.loadClass(HadoopGroupingTest.class.getName()));

        suite.add(ldr.loadClass(HadoopClientProtocolSelfTest.class.getName()));
        suite.add(ldr.loadClass(HadoopClientProtocolEmbeddedSelfTest.class.getName()));
        suite.add(ldr.loadClass(HadoopClientProtocolMultipleServersSelfTest.class.getName()));

        suite.add(ldr.loadClass(HadoopCommandLineTest.class.getName()));

        suite.add(ldr.loadClass(HadoopSecondaryFileSystemConfigurationTest.class.getName()));

        suite.add(ldr.loadClass(HadoopTxConfigCacheTest.class.getName()));

        suite.add(ldr.loadClass(IgniteHadoopFileSystemClientBasedOpenTest.class.getName()));

         return suite;
    }

    /**
     * @throws Exception If failed.
     */
    public static void downloadHive() throws Exception {
        String ver = IgniteSystemProperties.getString("hive.version", "1.2.1");

        X.println("Will use Hive version: " + ver);

        String downloadPath = "hive/hive-" + ver + "/apache-hive-" + ver + "-bin.tar.gz";

        download("Hive", "HIVE_HOME", downloadPath, "apache-hive-" + ver + "-bin");
    }

    /**
     * @throws Exception If failed.
     */
    public static void downloadHadoop() throws Exception {
        String ver = IgniteSystemProperties.getString("hadoop.version", "2.4.1");

        X.println("Will use Hadoop version: " + ver);

        String downloadPath = "hadoop/core/hadoop-" + ver + "/hadoop-" + ver + ".tar.gz";

        download("Hadoop", "HADOOP_HOME", downloadPath, "hadoop-" + ver);
    }

    /**
     *  Downloads and extracts an Apache product.
     *
     * @param appName Name of application for log messages.
     * @param homeVariable Pointer to home directory of the component.
     * @param downloadPath Relative download path of tar package.
     * @param destName Local directory name to install component.
     * @throws Exception If failed.
     */
    private static void download(String appName, String homeVariable, String downloadPath, String destName)
        throws Exception {
        String homeVal = IgniteSystemProperties.getString(homeVariable);

        if (!F.isEmpty(homeVal) && new File(homeVal).isDirectory()) {
            X.println(homeVariable + " is set to: " + homeVal);

            return;
        }

        List<String> urls = F.asList(
            "http://archive.apache.org/dist/",
            "http://apache-mirror.rbc.ru/pub/apache/",
            "http://www.eu.apache.org/dist/",
            "http://www.us.apache.org/dist/");

        String tmpPath = System.getProperty("java.io.tmpdir");

        X.println("tmp: " + tmpPath);

        final File install = new File(tmpPath + File.separatorChar + "__hadoop");

        final File home = new File(install, destName);

        X.println("Setting " + homeVariable + " to " + home.getAbsolutePath());

        System.setProperty(homeVariable, home.getAbsolutePath());

        final File successFile = new File(home, "__success");

        if (home.exists()) {
            if (successFile.exists()) {
                X.println(appName + " distribution already exists.");

                return;
            }

            X.println(appName + " distribution is invalid and it will be deleted.");

            if (!U.delete(home))
                throw new IOException("Failed to delete directory: " + home.getAbsolutePath());
        }

        for (String url : urls) {
            if (!(install.exists() || install.mkdirs()))
                throw new IOException("Failed to create directory: " + install.getAbsolutePath());

            URL u = new URL(url + downloadPath);

            X.println("Attempting to download from: " + u);

            try {
                URLConnection c = u.openConnection();

                c.connect();

                try (TarArchiveInputStream in = new TarArchiveInputStream(new GzipCompressorInputStream(
                    new BufferedInputStream(c.getInputStream(), 32 * 1024)))) {

                    TarArchiveEntry entry;

                    while ((entry = in.getNextTarEntry()) != null) {
                        File dest = new File(install, entry.getName());

                        if (entry.isDirectory()) {
                            if (!dest.mkdirs())
                                throw new IllegalStateException();
                        }
                        else if (entry.isSymbolicLink()) {
                            // Important: in Hadoop installation there are symlinks, we need to create them:
                            Path theLinkItself = Paths.get(install.getAbsolutePath(), entry.getName());

                            Path linkTarget = Paths.get(entry.getLinkName());

                            Files.createSymbolicLink(theLinkItself, linkTarget);
                        }
                        else {
                            File parent = dest.getParentFile();

                            if (!(parent.exists() || parent.mkdirs()))
                                throw new IllegalStateException();

                            X.print(" [" + dest);

                            try (BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(dest, false),
                                    128 * 1024)) {
                                U.copy(in, out);

                                out.flush();
                            }

                            Files.setPosixFilePermissions(dest.toPath(), modeToPermissionSet(entry.getMode()));

                            X.println("]");
                        }
                    }
                }

                if (successFile.createNewFile())
                    return;
            }
            catch (Exception e) {
                e.printStackTrace();

                U.delete(home);
            }
        }

        throw new IllegalStateException("Failed to install " + appName + ".");
    }
}
