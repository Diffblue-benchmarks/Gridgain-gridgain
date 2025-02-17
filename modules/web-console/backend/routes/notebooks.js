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

const express = require('express');
const _ = require('lodash');

// Fire me up!

module.exports = {
    implements: 'routes/notebooks',
    inject: ['mongo', 'services/spaces', 'services/notebooks']
};

module.exports.factory = (mongo, spacesService, notebooksService) => {
    return new Promise((factoryResolve) => {
        const router = new express.Router();

        /**
         * Get notebooks names accessed for user account.
         *
         * @param req Request.
         * @param res Response.
         */
        router.get('/', (req, res) => {
            return spacesService.spaces(req.currentUserId())
                .then((spaces) => _.map(spaces, (space) => space._id))
                .then((spaceIds) => notebooksService.listBySpaces(spaceIds))
                .then(res.api.ok)
                .catch(res.api.error);
        });

        /**
         * Save notebook accessed for user account.
         *
         * @param req Request.
         * @param res Response.
         */
        router.post('/save', (req, res) => {
            const notebook = req.body;

            spacesService.spaceIds(req.currentUserId())
                .then((spaceIds) => {
                    notebook.space = notebook.space || spaceIds[0];

                    return notebooksService.merge(notebook);
                })
                .then(res.api.ok)
                .catch(res.api.error);
        });

        /**
         * Remove notebook by ._id.
         *
         * @param req Request.
         * @param res Response.
         */
        router.post('/remove', (req, res) => {
            const notebookId = req.body._id;

            notebooksService.remove(notebookId)
                .then(res.api.ok)
                .catch(res.api.error);
        });

        factoryResolve(router);
    });
};
