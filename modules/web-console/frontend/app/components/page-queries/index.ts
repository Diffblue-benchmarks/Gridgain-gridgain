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

import './style.scss';

import angular from 'angular';

import queriesNotebooksList from './components/queries-notebooks-list';
import queriesNotebook from './components/queries-notebook';
import pageQueriesCmp from './component';
import {default as ActivitiesData} from 'app/core/activities/Activities.data';
import Notebook from './notebook.service';
import {navigationMenuItem, AppStore} from '../../store';

/**
 * @param {import('@uirouter/angularjs').UIRouter} $uiRouter
 * @param {ActivitiesData} ActivitiesData
 */
function registerActivitiesHook($uiRouter, ActivitiesData) {
    $uiRouter.transitionService.onSuccess({to: 'base.sql.**'}, (transition) => {
        ActivitiesData.post({group: 'sql', action: transition.targetState().name()});
    });
}

registerActivitiesHook.$inject = ['$uiRouter', 'IgniteActivitiesData'];

export default angular.module('ignite-console.sql', [
    'ui.router',
    queriesNotebooksList.name,
    queriesNotebook.name
])
    .run(['Store', (store: AppStore) => {
        store.dispatch(navigationMenuItem({
            activeSref: 'base.sql.**',
            icon: 'sql',
            label: 'Queries',
            order: 2,
            sref: 'base.sql.tabs.notebooks-list'
        }));
    }])
    .component('pageQueries', pageQueriesCmp)
    .component('pageQueriesSlot', {
        require: {
            pageQueries: '^pageQueries'
        },
        bindings: {
            slotName: '<'
        },
        controller: class {
            static $inject = ['$transclude', '$timeout'];

            constructor($transclude, $timeout) {
                this.$transclude = $transclude;
                this.$timeout = $timeout;
            }

            $postLink() {
                this.$transclude((clone) => {
                    this.pageQueries[this.slotName].empty();
                    clone.appendTo(this.pageQueries[this.slotName]);
                });
            }
        },
        transclude: true
    })
    .service('IgniteNotebook', Notebook)
    .config(['$stateProvider', ($stateProvider) => {
        // set up the states
        $stateProvider
            .state('base.sql', {
                abstract: true
            })
            .state('base.sql.tabs', {
                url: '/queries',
                component: 'pageQueries',
                redirectTo: 'base.sql.tabs.notebooks-list',
                permission: 'query'
            })
            .state('base.sql.tabs.notebooks-list', {
                url: '/notebooks',
                component: 'queriesNotebooksList',
                permission: 'query',
                tfMetaTags: {
                    title: 'Notebooks'
                }
            })
            .state('base.sql.notebook', {
                url: '/notebook/{noteId}',
                component: 'queriesNotebook',
                permission: 'query',
                tfMetaTags: {
                    title: 'Query notebook'
                }
            });
    }])
    .run(registerActivitiesHook);
