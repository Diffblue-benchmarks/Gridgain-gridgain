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

import angular from 'angular';

import _ from 'lodash';

import template from './template.pug';
import controller from './controller';
import publicTemplate from '../../../views/public.pug';

import './style.scss';

export default angular
    .module('ignite-console.page-password-reset', [
    ])
    .component('pagePasswordReset', {
        template,
        controller
    })
    .config(['$stateProvider', ($stateProvider) => {
        // set up the states
        $stateProvider
        .state('password', {
            url: '/password',
            abstract: true,
            template: '<ui-view></ui-view>'
        })
        .state('password.reset', {
            url: '/reset?{token}',
            views: {
                '@': {
                    template: publicTemplate
                },
                'page@password.reset': {
                    component: 'pagePasswordReset'
                }
            },
            redirectTo: (trans) => {
                if (_.isEmpty(trans.params('to').token))
                    return 'signin';

                return true;
            },
            unsaved: true,
            tfMetaTags: {
                title: 'Reset password'
            }
        });
    }]);
