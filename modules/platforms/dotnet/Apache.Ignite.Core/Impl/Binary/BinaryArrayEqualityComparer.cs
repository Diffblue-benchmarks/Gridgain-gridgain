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

namespace Apache.Ignite.Core.Impl.Binary
{
    using System;
    using System.Collections.Generic;
    using System.Diagnostics;
    using Apache.Ignite.Core.Binary;
    using Apache.Ignite.Core.Impl.Binary.IO;

    /// <summary>
    /// Compares binary object equality using underlying byte array.
    /// </summary>
    internal static class BinaryArrayEqualityComparer
    {
        /** */
        private static readonly HashStreamProcessor HashCodeProcessor = new HashStreamProcessor();

        /// <summary>
        /// Determines whether the specified objects are equal.
        /// </summary>
        /// <param name="x">The first object to compare.</param>
        /// <param name="y">The second object to compare.</param>
        /// <returns>
        /// true if the specified objects are equal; otherwise, false.
        /// </returns>
        public static bool Equals(IBinaryObject x, IBinaryObject y)
        {
            if (x == null)
                return y == null;

            if (y == null)
                return false;

            if (ReferenceEquals(x, y))
                return true;

            var binx = GetBinaryObject(x);
            var biny = GetBinaryObject(y);

            var lenx = GetDataLength(binx);
            var leny = GetDataLength(biny);

            if (lenx != leny)
                return false;

            var startx = GetDataStart(binx);
            var starty = GetDataStart(biny);

            var arrx = binx.Data;
            var arry = biny.Data;

            for (var i = 0; i < lenx; i++)
            {
                if (arrx[i + startx] != arry[i + starty])
                    return false;
            }

            return true;
        }

        /// <summary>
        /// Returns a hash code for this instance.
        /// </summary>
        /// <param name="obj">The object.</param>
        /// <returns>
        /// A hash code for this instance, suitable for use in hashing algorithms and data structures like a hash table. 
        /// </returns>
        public static int GetHashCode(IBinaryObject obj)
        {
            if (obj == null)
                return 0;

            var binObj = GetBinaryObject(obj);

            var arg = new KeyValuePair<int, int>(GetDataStart(binObj), GetDataLength(binObj));

            using (var stream = new BinaryHeapStream(binObj.Data))
            {
                return stream.Apply(HashCodeProcessor, arg);
            }
        }

        /** <inheritdoc /> */
        public static int GetHashCode(IBinaryStream stream, int startPos, int length)
        {
            Debug.Assert(stream != null);
            Debug.Assert(startPos >= 0);
            Debug.Assert(length >= 0);

            var arg = new KeyValuePair<int, int>(startPos, length);

            return stream.Apply(HashCodeProcessor, arg);
        }

        /// <summary>
        /// Casts to <see cref="BinaryObject"/> or throws an error.
        /// </summary>
        private static BinaryObject GetBinaryObject(IBinaryObject obj)
        {
            var binObj = obj as BinaryObject;

            if (binObj != null)
                return binObj;

            throw new NotSupportedException(string.Format("{0} of type {1} is not supported.",
                typeof(IBinaryObject), obj.GetType()));
        }

        /// <summary>
        /// Gets the non-raw data length.
        /// </summary>
        private static int GetDataLength(BinaryObject binObj)
        {
            return binObj.Header.FooterStartOffset - BinaryObjectHeader.Size;
        }

        /// <summary>
        /// Gets the data starting position.
        /// </summary>
        private static int GetDataStart(BinaryObject binObj)
        {
            return binObj.Offset + BinaryObjectHeader.Size;
        }

        /// <summary>
        /// Hash code calculating stream processor.
        /// </summary>
        private class HashStreamProcessor : IBinaryStreamProcessor<KeyValuePair<int, int>, int>
        {
            /** <inheritdoc /> */
            public unsafe int Invoke(byte* data, KeyValuePair<int, int> arg)
            {
                int hash = 1;
                sbyte* ptr = (sbyte*) (data + arg.Key);

                for (var i = 0; i < arg.Value; i++)
                {
                    hash = 31 * hash + *(ptr + i);
                }

                return hash;
            }
        }
    }
}
