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

namespace Apache.Ignite.Core.Tests.Cache
{
    using System.IO;
    using Apache.Ignite.Core.Tests.Process;
    using NUnit.Framework;

    /// <summary>
    /// Tests cache with a standalone process.
    /// </summary>
    public class CacheForkedTest
    {
        /** */
        private IIgnite _grid;

        /// <summary>
        /// Set up.
        /// </summary>
        [TestFixtureSetUp]
        public void SetUp()
        {
            const string springConfigUrl = "config\\compute\\compute-grid1.xml";
            
            // ReSharper disable once UnusedVariable
            var proc = new IgniteProcess(
                "-jvmClasspath=" + TestUtils.CreateTestClasspath(),
                "-springConfigUrl=" + Path.GetFullPath(springConfigUrl),
                "-J-ea",
                "-J-Xcheck:jni",
                "-J-Xms512m",
                "-J-Xmx512m",
                "-J-DIGNITE_QUIET=false"
                );
            Assert.IsTrue(proc.Alive);

            _grid = Ignition.Start(new IgniteConfiguration(TestUtils.GetTestConfiguration())
            {
                SpringConfigUrl = springConfigUrl
            });

            Assert.IsTrue(_grid.WaitTopology(2));
        }

        /// <summary>
        /// Tear down.
        /// </summary>
        [TestFixtureTearDown]
        public void TearDown()
        {
            IgniteProcess.KillAll();

            Ignition.StopAll(true);
        }

        /// <summary>
        /// Tests cache clear.
        /// </summary>
        [Test]
        public void TestClearCache()
        {
            _grid.GetCache<object, object>("default").Clear();
        }
    }
}