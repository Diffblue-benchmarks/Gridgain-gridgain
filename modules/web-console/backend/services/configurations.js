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
    implements: 'services/configurations',
    inject: ['mongo', 'services/spaces', 'services/clusters', 'services/caches', 'services/domains', 'services/igfss']
};

/**
 * @param mongo
 * @param {SpacesService} spacesService
 * @param {ClustersService} clustersService
 * @param {CachesService} cachesService
 * @param {DomainsService} domainsService
 * @param {IgfssService} igfssService
 * @returns {ConfigurationsService}
 */
module.exports.factory = (mongo, spacesService, clustersService, cachesService, domainsService, igfssService) => {
    class ConfigurationsService {
        static list(userId, demo) {
            let spaces;

            return spacesService.spaces(userId, demo)
                .then((_spaces) => {
                    spaces = _spaces;

                    return spaces.map((space) => space._id);
                })
                .then((spaceIds) => Promise.all([
                    clustersService.listBySpaces(spaceIds),
                    domainsService.listBySpaces(spaceIds),
                    cachesService.listBySpaces(spaceIds),
                    igfssService.listBySpaces(spaceIds)
                ]))
                .then(([clusters, domains, caches, igfss]) => ({clusters, domains, caches, igfss, spaces}));
        }

        static get(userId, demo, _id) {
            return clustersService.get(userId, demo, _id)
                .then((cluster) =>
                    Promise.all([
                        mongo.Cache.find({space: cluster.space, _id: {$in: cluster.caches}}).lean().exec(),
                        mongo.DomainModel.find({space: cluster.space, _id: {$in: cluster.models}}).lean().exec(),
                        mongo.Igfs.find({space: cluster.space, _id: {$in: cluster.igfss}}).lean().exec()
                    ])
                        .then(([caches, models, igfss]) => ({cluster, caches, models, igfss}))
                );
        }
    }

    return ConfigurationsService;
};
