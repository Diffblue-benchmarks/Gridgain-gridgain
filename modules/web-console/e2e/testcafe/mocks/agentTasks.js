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

export const taskResult = (data) => ({
    data,
    error: null,
    sessionToken: null,
    zipped: false,
    status: 0
});

export const cacheNamesCollectorTask = (caches) => (ws) => {
    ws.on('connection', (s) => {
        s.on('node:visor', (e, ack) => {
            if (e.params.taskId === 'cacheNamesCollectorTask')
                ack(null, taskResult(caches));
        });
    });
};

export const simeplFakeSQLQuery = (nid, response) => (ws) => {
    ws.on('connection', (s) => {
        s.on('node:visor', (e, ack) => {
            switch (e.params.taskId) {
                case 'cacheNodesTaskX2':
                    return ack(null, taskResult([nid]));

                case 'querySqlX2': {
                    if (e.params.nids === nid) {
                        return ack(null, taskResult({
                            error: null,
                            result: {
                                columns: null,
                                duration: 0,
                                hasMore: false,
                                queryId: 'query-1',
                                responseNodeId: nid,
                                rows: null
                            }
                        }));
                    }
                }

                case 'queryFetchFirstPage': {
                    if (e.params.nids === nid)
                        return ack(null, taskResult(response));
                }
            }
        });
    });
};

export const FAKE_CLUSTERS = {
    count: 2,
    hasDemo: true,
    clusters: [
        {
            id: '70831a7c-2b5e-4c11-8c08-5888911d5962',
            name: 'Cluster 1',
            nids: ['143048f1-b5b8-47d6-9239-fed76222efe3'],
            addresses: {
                '143048f1-b5b8-47d6-9239-fed76222efe3': '10.0.75.1'
            },
            clients: {
                '143048f1-b5b8-47d6-9239-fed76222efe3': false
            },
            clusterVersion: '8.8.0-SNAPSHOT',
            active: true,
            secured: false
        },
        {
            id: '70831a7c-2b5e-4c11-8c08-5888911d5963',
            name: 'Cluster 2',
            nids: ['143048f1-b5b8-47d6-9239-fed76222efe4'],
            addresses: {
                '143048f1-b5b8-47d6-9239-fed76222efe3': '10.0.75.1'
            },
            clients: {
                '143048f1-b5b8-47d6-9239-fed76222efe3': false
            },
            clusterVersion: '8.8.0-SNAPSHOT',
            active: true,
            secured: false
        }
    ]
};

export const SIMPLE_QUERY_RESPONSE = {
    error: null,
    result: {
        rows: [
            [1, 'Ed'],
            [2, 'Ann'],
            [3, 'Emma']
        ],
        hasMore: false,
        duration: 0,
        columns: [{
            schemaName: 'PUBLIC',
            typeName: 'PERSON',
            fieldName: 'ID',
            fieldTypeName: 'java.lang.Integer'
        }, {
            schemaName: 'PUBLIC',
            typeName: 'PERSON',
            fieldName: 'NAME',
            fieldTypeName: 'java.lang.String'
        }],
        queryId: 'VISOR_SQL_QUERY-42b1b723-874e-48eb-a760-b6357fc71c7f',
        responseNodeId: '0daf9042-21e6-4dd3-8f8e-a3187246abe4'
    }
};

export const FAKE_CACHES = {
    caches: {
        Cache1: 'a',
        Cache2: 'b'
    },
    groups: []
};

export const agentStat = (clusters) => (ws) => {
    ws.on('connection', ((e) => {
        ws.emit('agents:stat', clusters);
    }));
};
