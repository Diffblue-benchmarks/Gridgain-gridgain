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

import {StateService} from '@uirouter/angularjs';

interface IActivityDataResponse {
    action: string,
    amount: number,
    date: string,
    group: string,
    owner: string,
    _id: string
}

export default class ActivitiesData {
    static $inject = ['$http', '$state'];

    constructor(private $http: ng.IHttpService, private $state: StateService) {}

    /**
     * Posts activity to backend, sends current state if no options specified
     */
    // For some reason, Babel loses this after destructuring, the arrow helps with that
    post = (options: {group?: string, action?: string} = {}) => {
        let { group, action } = options;

        // TODO IGNITE-5466: since upgrade to UIRouter 1, "url.source" is undefined.
        // Actions like that won't be saved to DB. Think of a better solution later.
        action = action || this.$state.$current.url.source || '';
        group = group || (action.match(/^\/([^/]+)/) || [])[1];

        return this.$http.post<IActivityDataResponse>('/api/v1/activities/page', { group, action })
            .catch(() => {
                // No-op.
            });
    }
}
