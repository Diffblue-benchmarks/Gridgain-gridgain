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

#pragma warning disable 618  // Obsolete.
namespace Apache.Ignite.Core.Tests.Cache
{
    using System.IO;
    using Apache.Ignite.Core.Common;
    using Apache.Ignite.Core.Impl;
    using Apache.Ignite.Core.PersistentStore;
    using NUnit.Framework;

    /// <summary>
    /// Tests the persistent store. Uses the obsolete API. See <see cref="PersistenceTest"/> for the actual API.
    /// </summary>
    [Category(TestUtils.CategoryIntensive)]
    public class PersistentStoreTestObsolete
    {
        /** Temp dir for WAL. */
        private readonly string _tempDir = IgniteUtils.GetTempDirectoryName();

        /// <summary>
        /// Tears down the test.
        /// </summary>
        [TearDown]
        public void TearDown()
        {
            Ignition.StopAll(true);

            if (Directory.Exists(_tempDir))
            {
                Directory.Delete(_tempDir, true);
            }
        }

        /// <summary>
        /// Tests that cache data survives node restart.
        /// </summary>
        [Test]
        public void TestCacheDataSurvivesNodeRestart()
        {
            var cfg = new IgniteConfiguration(TestUtils.GetTestConfiguration())
            {
                PersistentStoreConfiguration = new PersistentStoreConfiguration
                {
                    PersistentStorePath = Path.Combine(_tempDir, "Store"),
                    WalStorePath = Path.Combine(_tempDir, "WalStore"),
                    WalArchivePath = Path.Combine(_tempDir, "WalArchive"),
                    MetricsEnabled = true
                },
                DataStorageConfiguration = null
            };

            const string cacheName = "persistentCache";

            // Start Ignite, put data, stop.
            using (var ignite = Ignition.Start(cfg))
            {
                ignite.SetActive(true);

                var cache = ignite.CreateCache<int, int>(cacheName);

                cache[1] = 1;

                // Check some metrics.
                var metrics = ignite.GetPersistentStoreMetrics();
                Assert.Greater(metrics.WalLoggingRate, 0);
                Assert.Greater(metrics.WalFsyncTimeAverage, 0);
            }

            // Verify directories.
            Assert.IsTrue(Directory.Exists(cfg.PersistentStoreConfiguration.PersistentStorePath));
            Assert.IsTrue(Directory.Exists(cfg.PersistentStoreConfiguration.WalStorePath));
            Assert.IsTrue(Directory.Exists(cfg.PersistentStoreConfiguration.WalArchivePath));

            // Start Ignite, verify data survival.
            using (var ignite = Ignition.Start(cfg))
            {
                ignite.SetActive(true);

                var cache = ignite.GetCache<int, int>(cacheName);

                Assert.AreEqual(1, cache[1]);
            }

            // Delete store directory.
            Directory.Delete(_tempDir, true);

            // Start Ignite, verify data loss.
            using (var ignite = Ignition.Start(cfg))
            {
                ignite.SetActive(true);

                Assert.IsFalse(ignite.GetCacheNames().Contains(cacheName));
            }
        }

        /// <summary>
        /// Tests the grid activation with persistence (inactive by default).
        /// </summary>
        [Test]
        public void TestGridActivationWithPersistence()
        {
            var cfg = new IgniteConfiguration(TestUtils.GetTestConfiguration())
            {
                PersistentStoreConfiguration = new PersistentStoreConfiguration(),
                DataStorageConfiguration = null
            };

            // Default config, inactive by default (IsActiveOnStart is ignored when persistence is enabled).
            using (var ignite = Ignition.Start(cfg))
            {
                CheckIsActive(ignite, false);

                ignite.SetActive(true);
                CheckIsActive(ignite, true);

                ignite.SetActive(false);
                CheckIsActive(ignite, false);
            }
        }

        /// <summary>
        /// Tests the grid activation without persistence (active by default).
        /// </summary>
        [Test]
        public void TestGridActivationNoPersistence()
        {
            var cfg = TestUtils.GetTestConfiguration();
            Assert.IsTrue(cfg.IsActiveOnStart);

            using (var ignite = Ignition.Start(cfg))
            {
                CheckIsActive(ignite, true);

                ignite.SetActive(false);
                CheckIsActive(ignite, false);

                ignite.SetActive(true);
                CheckIsActive(ignite, true);
            }

            cfg.IsActiveOnStart = false;

            using (var ignite = Ignition.Start(cfg))
            {
                CheckIsActive(ignite, false);

                ignite.SetActive(true);
                CheckIsActive(ignite, true);

                ignite.SetActive(false);
                CheckIsActive(ignite, false);
            }
        }

        /// <summary>
        /// Checks active state.
        /// </summary>
        private static void CheckIsActive(IIgnite ignite, bool isActive)
        {
            Assert.AreEqual(isActive, ignite.IsActive());

            if (isActive)
            {
                var cache = ignite.GetOrCreateCache<int, int>("default");
                cache[1] = 1;
                Assert.AreEqual(1, cache[1]);
            }
            else
            {
                var ex = Assert.Throws<IgniteException>(() => ignite.GetOrCreateCache<int, int>("default"));
                Assert.AreEqual("Can not perform the operation because the cluster is inactive.",
                    ex.Message.Substring(0, 62));
            }
        }
    }
}
