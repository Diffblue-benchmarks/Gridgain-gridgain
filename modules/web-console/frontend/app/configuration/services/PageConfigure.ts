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

import cloneDeep from 'lodash/cloneDeep';

import {merge, timer} from 'rxjs';
import {take, tap, ignoreElements, filter, map, pluck} from 'rxjs/operators';

import {
    ofType
} from '../store/effects';

import {default as ConfigureState} from './ConfigureState';
import {default as ConfigSelectors} from '../store/selectors';

export default class PageConfigure {
    static $inject = ['ConfigureState', 'ConfigSelectors'];

    constructor(private ConfigureState: ConfigureState, private ConfigSelectors: ConfigSelectors) {}

    getClusterConfiguration({clusterID, isDemo}: {clusterID: string, isDemo: boolean}) {
        return merge(
            timer(1).pipe(
                take(1),
                tap(() => this.ConfigureState.dispatchAction({type: 'LOAD_COMPLETE_CONFIGURATION', clusterID, isDemo})),
                ignoreElements()
            ),
            this.ConfigureState.actions$.pipe(
                ofType('LOAD_COMPLETE_CONFIGURATION_ERR'),
                take(1),
                pluck('error'),
                map((e) => Promise.reject(e))
            ),
            this.ConfigureState.state$.pipe(
                this.ConfigSelectors.selectCompleteClusterConfiguration({clusterID, isDemo}),
                filter((c) => c.__isComplete),
                take(1),
                map((data) => ({...data, clusters: [cloneDeep(data.cluster)]}))
            )
        ).pipe(take(1))
        .toPromise();
    }
}
