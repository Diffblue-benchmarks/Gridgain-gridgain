#
# Copyright 2019 GridGain Systems, Inc. and Contributors.
#
# Licensed under the GridGain Community Edition License (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     https://www.gridgain.com/products/software/community-edition/gridgain-community-edition-license
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
#                                               -*- Autoconf -*-
# Process this file with autoconf to produce a configure script.

AC_PREREQ([2.69])
AC_INIT([Apache Ignite JNI bridge for C++], [8.8.0.36920], [dev@ignite.apache.org], [ignite-jni], [ignite.apache.org])
AC_CONFIG_SRCDIR(src)

AC_CANONICAL_SYSTEM
AC_CONFIG_MACRO_DIR([m4])
AC_LANG([C++])

# Initialize automake
AM_INIT_AUTOMAKE([-Wall foreign subdir-objects])
AC_CONFIG_HEADER(config.h)

AM_PROG_AR

# Checks for programs.
GXX="-g -O2"

AC_PROG_CXX

# Initialize Libtool
LT_INIT

# Checks for libraries.
AC_CHECK_LIB([pthread], [pthread_mutex_lock])

# Checks for header files.

# Checks for typedefs, structures, and compiler characteristics.
AC_C_INLINE
AC_TYPE_INT16_T
AC_TYPE_INT32_T
AC_TYPE_INT64_T
AC_TYPE_INT8_T
AC_TYPE_PID_T
AC_TYPE_SIZE_T

# Checks for library functions.
AC_FUNC_ERROR_AT_LINE

AC_CONFIG_FILES(Makefile include/Makefile ignite-jni.pc)

AC_OUTPUT
