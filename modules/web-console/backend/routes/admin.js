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

// Fire me up!

module.exports = {
    implements: 'routes/admin',
    inject: ['settings', 'mongo', 'services/spaces', 'services/sessions', 'services/users', 'services/notifications']
};

/**
 * @param settings
 * @param mongo
 * @param spacesService
 * @param {SessionsService} sessionsService
 * @param {UsersService} usersService
 * @param {NotificationsService} notificationsService
 * @returns {Promise}
 */
module.exports.factory = function(settings, mongo, spacesService, sessionsService, usersService, notificationsService) {
    return new Promise((factoryResolve) => {
        const router = new express.Router();

        /**
         * Get list of user accounts.
         */
        router.post('/list', (req, res) => {
            usersService.list(req.body)
                .then(res.api.ok)
                .catch(res.api.error);
        });

        // Remove user.
        router.post('/remove', (req, res) => {
            usersService.remove(req.origin(), req.body.userId)
                .then(res.api.ok)
                .catch(res.api.error);
        });

        // Grant or revoke admin access to user.
        router.post('/toggle', (req, res) => {
            const params = req.body;

            mongo.Account.findByIdAndUpdate(params.userId, {admin: params.adminFlag}).exec()
                .then(res.api.ok)
                .catch(res.api.error);
        });

        // Become user.
        router.get('/become', (req, res) => {
            sessionsService.become(req.session, req.query.viewedUserId)
                .then(res.api.ok)
                .catch(res.api.error);
        });

        // Revert to your identity.
        router.get('/revert/identity', (req, res) => {
            sessionsService.revert(req.session)
                .then(res.api.ok)
                .catch(res.api.error);
        });

        // Update notifications.
        router.put('/notifications', (req, res) => {
            notificationsService.merge(req.user._id, req.body.message, req.body.isShown)
                .then(res.api.done)
                .catch(res.api.error);
        });

        factoryResolve(router);
    });
};

