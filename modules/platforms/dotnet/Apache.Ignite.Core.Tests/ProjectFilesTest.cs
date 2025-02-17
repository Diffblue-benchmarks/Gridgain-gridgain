﻿/*
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

namespace Apache.Ignite.Core.Tests
{
    using System;
    using System.Collections.Generic;
    using System.IO;
    using System.Linq;
    using System.Reflection;
    using System.Text.RegularExpressions;
    using NUnit.Framework;

    /// <summary>
    /// Verifies source files.
    /// </summary>
    public class ProjectFilesTest
    {
        /// <summary>
        /// Tests that tools version is compatible with VS2010.
        /// </summary>
        [Test]
        public void TestCsprojToolsVersion()
        {
            var projFiles = GetDotNetSourceDir().GetFiles("*.csproj", SearchOption.AllDirectories)
                .Where(x => !x.FullName.ToLower().Contains("dotnetcore")).ToArray();
            
            Assert.GreaterOrEqual(projFiles.Length, 7);
            CheckFiles(projFiles, x => !x.Contains("ToolsVersion=\"4.0\""), "Invalid csproj files: ");
        }

        /// <summary>
        /// Tests that release build settings are correct: XML docs are generated.
        /// </summary>
        [Test]
        public void TestCsprojReleaseDocs()
        {
            CheckFiles(GetReleaseCsprojFiles(), x => !GetReleaseSection(x).Contains("DocumentationFile"), 
                "Missing XML doc in release mode: ");
        }

        /// <summary>
        /// Tests that release build settings are correct: there are no DEBUG/TRACE constants.
        /// </summary>
        [Test]
        public void TestCsprojBuildSettings()
        {
            CheckFiles(GetReleaseCsprojFiles(), x => GetReleaseSection(x).Contains("DefineConstants"), 
                "Invalid constants in release mode: ");
        }

        /// <summary>
        /// Tests that release build settings are correct: debug information is disabled.
        /// </summary>
        [Test]
        public void TestCsprojPdbSettings()
        {
            CheckFiles(GetReleaseCsprojFiles(), x => !GetReleaseSection(x).Contains("<DebugType>none</DebugType>"), 
                "Invalid DebugType in release mode: ");
        }

        /// <summary>
        /// Tests that release build settings are correct: debug information is disabled.
        /// </summary>
        [Test]
        public void TestCsprojOptimizeCode()
        {
            CheckFiles(GetReleaseCsprojFiles(), x => !GetReleaseSection(x).Contains("<Optimize>true</Optimize>"), 
                "Invalid optimize setting in release mode: ");
        }

        /// <summary>
        /// Gets the csproj files that go to the release binary package.
        /// </summary>
        private static IEnumerable<FileInfo> GetReleaseCsprojFiles()
        {
            return GetDotNetSourceDir().GetFiles("*.csproj", SearchOption.AllDirectories)
                .Where(x => x.Name != "Apache.Ignite.csproj" &&
                            !x.Name.Contains("Test") &&
                            !x.Name.Contains("Example") &&
                            !x.Name.Contains("DotNetCore") &&
                            !x.Name.Contains("Benchmark"));
        }

        /// <summary>
        /// Gets the release section.
        /// </summary>
        private static string GetReleaseSection(string csproj)
        {
            return Regex.Match(csproj, @"<PropertyGroup[^>]*Release\|AnyCPU(.*?)<\/PropertyGroup>", 
                RegexOptions.Singleline).Value;
        }

        /// <summary>
        /// Tests that tools version is compatible with VS2010.
        /// </summary>
        [Test]
        public void TestSlnToolsVersion()
        {
            var slnFiles = GetDotNetSourceDir().GetFiles("*.sln", SearchOption.AllDirectories)
                .Where(x => !x.Name.Contains("DotNetCore")).ToArray();

            Assert.GreaterOrEqual(slnFiles.Length, 2);
            CheckFiles(slnFiles, x => !x.Contains("# Visual Studio 2010") ||
                                      !x.Contains("Microsoft Visual Studio Solution File, Format Version 11.00"),
                "Invalid sln files: ");
        }

        /// <summary>
        /// Tests that there are no non-ASCII chars.
        /// </summary>
        [Test]
        public void TestAsciiChars()
        {
            var allowedFiles = new[]
            {
                "BinaryStringTest.cs",
                "BinarySelfTest.cs", 
                "CacheDmlQueriesTest.cs",
                "CacheTest.cs"
            };

            var srcFiles = GetDotNetSourceDir()
                .GetFiles("*.cs", SearchOption.AllDirectories)
                .Where(x => !allowedFiles.Contains(x.Name));

            CheckFiles(srcFiles, x => x.Any(ch => ch > 255), "Files with non-ASCII chars: ");
        }

        /// <summary>
        /// Checks the files.
        /// </summary>
        private static void CheckFiles(IEnumerable<FileInfo> files, Func<string, bool> isInvalid, string errorText)
        {
            var invalidFiles = files.Where(x => isInvalid(File.ReadAllText(x.FullName))).ToArray();

            Assert.AreEqual(0, invalidFiles.Length,
                errorText + string.Join("\n ", invalidFiles.Select(x => x.FullName)));
        }

        /// <summary>
        /// Gets the dot net source dir.
        /// </summary>
        private static DirectoryInfo GetDotNetSourceDir()
        {
            // ReSharper disable once AssignNullToNotNullAttribute
            var dir = new DirectoryInfo(Path.GetDirectoryName(Assembly.GetExecutingAssembly().Location));

            while (dir != null)
            {
                if (dir.GetFiles().Any(x => x.Name == "Apache.Ignite.sln"))
                    return dir;

                dir = dir.Parent;
            }

            throw new InvalidOperationException("Could not resolve Ignite.NET source directory.");
        }
    }
}
