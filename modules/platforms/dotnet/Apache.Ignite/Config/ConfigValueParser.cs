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

namespace Apache.Ignite.Config
{
    using System;

    /// <summary>
    /// Parses Ignite config values.
    /// </summary>
    internal class ConfigValueParser
    {
        /// <summary>
        /// Parses provided string to int. Throws a custom exception if failed.
        /// </summary>
        public static int ParseInt(string value, string propertyName)
        {
            int result;

            if (int.TryParse(value, out result))
                return result;

            throw new InvalidOperationException(
                string.Format("Failed to configure Ignite: property '{0}' has value '{1}', which is not an integer.",
                    propertyName, value));
        }
    }
}
