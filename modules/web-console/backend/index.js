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

const path = require('path');

const appPath = require('app-module-path');
appPath.addPath(__dirname);
appPath.addPath(path.join(__dirname, 'node_modules'));

const { migrate, init } = require('./launch-tools');

const injector = require('./injector');

injector.log.info = () => {};
injector.log.debug = () => {};

injector('mongo')
    .then((mongo) => migrate(mongo.connection, 'Ignite', path.join(__dirname, 'migrations')))
    .then(() => Promise.all([injector('settings'), injector('api-server'), injector('agents-handler'), injector('browsers-handler')]))
    .then(init)
    .catch((err) => {
        console.error(err);

        process.exit(1);
    });
