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

import publicTemplate from '../../../views/public.pug';
import {UIRouter, StateParams} from '@uirouter/angularjs';
import {IIgniteNg1StateDeclaration} from 'app/types';

export type PageSigninStateParams = StateParams & {activationToken?: string};

export function registerState($uiRouter: UIRouter) {
    const state: IIgniteNg1StateDeclaration = {
        url: '/signin?{activationToken:string}',
        name: 'signin',
        views: {
            '': {
                template: publicTemplate
            },
            'page@signin': {
                component: 'pageSignin'
            }
        },
        unsaved: true,
        redirectTo: (trans) => {
            const skipStates = new Set(['signup', 'forgotPassword', 'landing']);

            if (skipStates.has(trans.from().name))
                return;

            return trans.injector().get('User').read()
                .then(() => {
                    try {
                        const {name, params} = JSON.parse(localStorage.getItem('lastStateChangeSuccess'));

                        const restored = trans.router.stateService.target(name, params);

                        return restored.valid() ? restored : 'default-state';
                    }
                    catch (ignored) {
                        return 'default-state';
                    }
                })
                .catch(() => true);
        },
        tfMetaTags: {
            title: 'Sign In'
        },
        resolve: {
            activationToken() {
                return $uiRouter.stateService.transition.params<PageSigninStateParams>().activationToken;
            }
        }
    };

    $uiRouter.stateRegistry.register(state);
}

registerState.$inject = ['$uiRouter'];
