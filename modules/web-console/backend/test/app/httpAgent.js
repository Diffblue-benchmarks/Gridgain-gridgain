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

// Fire me up!

module.exports = {
    implements: 'agentFactory',
    inject: ['api-server', 'require(http)', 'require(supertest)']
};

module.exports.factory = (apiSrv, http, request) => {
    const express = apiSrv.attach(http.createServer());
    let authAgentInstance = null;

    return {
        authAgent: ({email, password}) => {
            if (authAgentInstance)
                return Promise.resolve(authAgentInstance);

            return new Promise((resolve, reject) => {
                authAgentInstance = request.agent(express);
                authAgentInstance.post('/api/v1/signin')
                    .send({email, password})
                    .end((err, res) => {
                        if (res.status === 401 || err)
                            return reject(err);

                        resolve(authAgentInstance);
                    });
            });
        },
        guestAgent: () => Promise.resolve(request.agent(express))
    };
};
