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

import StringBuilder from './StringBuilder';

/**
 * Properties generation entry point.
 */
export default class IgniteReadmeGenerator {
    header(sb) {
        sb.append('Content of this folder was generated by Apache Ignite Web Console');
        sb.append('=================================================================');
    }

    /**
     * Generate README.txt for jdbc folder.
     *
     * @param sb Resulting output with generated readme.
     * @returns {string} Generated content.
     */
    generateJDBC(sb = new StringBuilder()) {
        sb.append('Proprietary JDBC drivers for databases like Oracle, IBM DB2, Microsoft SQL Server are not available on Maven Central repository.');
        sb.append('Drivers should be downloaded manually and copied to this folder.');

        return sb.asString();
    }

    /**
     * Generate README.txt.
     *
     * @returns {string} Generated content.
     */
    generate(sb = new StringBuilder()) {
        this.header(sb);
        sb.emptyLine();

        sb.append('Project structure:');
        sb.append('    /jdbc-drivers - this folder should contains proprietary JDBC drivers.');
        sb.append('    /src - this folder contains generated java code.');
        sb.append('    /src/main/java/config - this folder contains generated java classes with cluster configuration from code.');
        sb.append('    /src/main/java/startup - this folder contains generated java classes with server and client nodes startup code.');
        sb.append('    /src/main/java/[model] - this optional folder will be named as package name for your POJO classes and contain generated POJO files.');
        sb.append('    /src/main/resources - this folder contains generated configurations in XML format and secret.properties file with security sensitive information if any.');
        sb.append('    Dockerfile - sample Docker file. With this file you could package Ignite deployment with all the dependencies into a standard container.');
        sb.append('    pom.xml - generated Maven project description, could be used to open generated project in IDE or build with Maven.');
        sb.append('    README.txt - this file.');

        sb.emptyLine();

        sb.append('Ignite ships with CacheJdbcPojoStore, which is out-of-the-box JDBC implementation of the IgniteCacheStore ');
        sb.append('interface, and automatically handles all the write-through and read-through logic.');

        sb.emptyLine();

        sb.append('You can use generated configuration and POJO classes as part of your application.');

        sb.emptyLine();

        sb.append('Note, in case of using proprietary JDBC drivers (Oracle, IBM DB2, Microsoft SQL Server)');
        sb.append('you should download them manually and copy into ./jdbc-drivers folder.');

        return sb.asString();
    }
}
