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

'use strict';

const fs = require('fs');
const path = require('path');
const _ = require('lodash');
const JSZip = require('jszip');

// Fire me up!

module.exports = {
    implements: 'services/agents',
    inject: ['settings', 'agents-handler', 'errors']
};

/**
 * @param settings
 * @param agentsHnd
 * @param errors
 * @returns {DownloadsService}
 */
module.exports.factory = (settings, agentsHnd, errors) => {
    class DownloadsService {
        /**
         * Get agent archive with user agent configuration.
         *
         * @returns {*} - readable stream for further piping. (http://stuk.github.io/jszip/documentation/api_jszip/generate_node_stream.html)
         */
        prepareArchive(host, token) {
            if (_.isEmpty(agentsHnd.currentAgent))
                throw new errors.MissingResourceException('Missing agent zip on server. Please ask webmaster to upload agent zip!');

            const {filePath, fileName} = agentsHnd.currentAgent;

            const folder = path.basename(fileName, '.zip');

            // Read a zip file.
            return new Promise((resolve, reject) => {
                fs.readFile(filePath, (errFs, data) => {
                    if (errFs)
                        reject(new errors.ServerErrorException(errFs));

                    JSZip.loadAsync(data)
                        .then((zip) => {
                            const prop = [];

                            prop.push(`tokens=${token}`);
                            prop.push(`server-uri=${host}`);
                            prop.push('#Uncomment following options if needed:');
                            prop.push('#node-uri=http://localhost:8080');
                            prop.push('#node-login=ignite');
                            prop.push('#node-password=ignite');
                            prop.push('#driver-folder=./jdbc-drivers');
                            prop.push('#Uncomment and configure following SSL options if needed:');
                            prop.push('#node-key-store=client.jks');
                            prop.push('#node-key-store-password=MY_PASSWORD');
                            prop.push('#node-trust-store=ca.jks');
                            prop.push('#node-trust-store-password=MY_PASSWORD');
                            prop.push('#server-key-store=client.jks');
                            prop.push('#server-key-store-password=MY_PASSWORD');
                            prop.push('#server-trust-store=ca.jks');
                            prop.push('#server-trust-store-password=MY_PASSWORD');
                            prop.push('#cipher-suites=CIPHER1,CIPHER2,CIPHER3');

                            zip.file(`${folder}/default.properties`, prop.join('\n'));

                            return zip.generateAsync({type: 'nodebuffer', platform: 'UNIX'})
                                .then((buffer) => resolve({filePath, fileName, buffer}));
                        })
                        .catch(reject);
                });
            });
        }
    }

    return new DownloadsService();
};
