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

using System;

#pragma warning disable 414  // Unused FuncPtr

namespace Apache.Ignite.Core.Impl.Unmanaged.Jni
{
    using System.Diagnostics.CodeAnalysis;
    using System.Runtime.InteropServices;

    /// <summary>
    /// JNINativeMethod structure for registering Java -> .NET callbacks.
    /// </summary>
    [SuppressMessage("Microsoft.Design", "CA1049:TypesThatOwnNativeResourcesShouldBeDisposable")]
    internal struct NativeMethod
    {
        /// <summary>
        /// Method name, char*.
        /// </summary>
        public IntPtr Name;

        /// <summary>
        /// Method signature, char*.
        /// </summary>
        public IntPtr Signature;

        /// <summary>
        /// Function pointer (from <see cref="Marshal.GetFunctionPointerForDelegate"/>).
        /// </summary>
        public IntPtr FuncPtr;
    }
}
