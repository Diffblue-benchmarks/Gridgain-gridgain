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

const assert = require('chai').assert;
const injector = require('../injector');

let agentFactory;
let db;

suite('routes.clusters', () => {
    suiteSetup(() => {
        return Promise.all([injector('agentFactory'), injector('dbHelper')])
            .then(([_agent, _db]) => {
                agentFactory = _agent;
                db = _db;
            });
    });

    setup(() => {
        return db.init();
    });

    test('Save cluster model', (done) => {
        const cluster = Object.assign({}, db.mocks.clusters[0], {name: 'newClusterName'});

        agentFactory.authAgent(db.mocks.accounts[0])
            .then((agent) => {
                agent.put('/api/v1/configuration/clusters')
                    .send({cluster})
                    .expect(200)
                    .expect((res) => {
                        assert.isNotNull(res.body);
                        assert.equal(res.body.rowsAffected, 1);
                    })
                    .end(done);
            })
            .catch(done);
    });

    test('Remove cluster model', (done) => {
        agentFactory.authAgent(db.mocks.accounts[0])
            .then((agent) => {
                agent.post('/api/v1/configuration/clusters/remove')
                    .send({_id: db.mocks.clusters[0]._id})
                    .expect(200)
                    .expect((res) => {
                        assert.isNotNull(res.body);
                        assert.equal(res.body.rowsAffected, 1);
                    })
                    .end(done);
            })
            .catch(done);
    });

    test('Remove all clusters', (done) => {
        agentFactory.authAgent(db.mocks.accounts[0])
            .then((agent) => {
                agent.post('/api/v1/configuration/clusters/remove/all')
                    .expect(200)
                    .expect((res) => {
                        assert.isNotNull(res.body);
                        assert.equal(res.body.rowsAffected, db.mocks.clusters.length);
                    })
                    .end(done);
            })
            .catch(done);
    });
});
