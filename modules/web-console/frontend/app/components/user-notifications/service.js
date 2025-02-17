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

import _ from 'lodash';

import controller from './controller';
import templateUrl from './template.tpl.pug';
import {CancellationError} from 'app/errors/CancellationError';

export default class UserNotificationsService {
    static $inject = ['$http', '$modal', '$q', 'IgniteMessages'];

    /** @type {ng.IQService} */
    $q;

    /**
     * @param {ng.IHttpService} $http    
     * @param {mgcrea.ngStrap.modal.IModalService} $modal   
     * @param {ng.IQService} $q       
     * @param {ReturnType<typeof import('app/services/Messages.service').default>} Messages
     */
    constructor($http, $modal, $q, Messages) {
        this.$http = $http;
        this.$modal = $modal;
        this.$q = $q;
        this.Messages = Messages;

        this.message = null;
        this.isShown = false;
    }

    set notification(notification) {
        this.message = _.get(notification, 'message');
        this.isShown = _.get(notification, 'isShown');
    }

    editor() {
        const deferred = this.$q.defer();

        const modal = this.$modal({
            templateUrl,
            resolve: {
                deferred: () => deferred,
                message: () => this.message,
                isShown: () => this.isShown
            },
            controller,
            controllerAs: '$ctrl'
        });

        const modalHide = modal.hide;

        modal.hide = () => deferred.reject(new CancellationError());

        return deferred.promise
            .finally(modalHide)
            .then(({ message, isShown }) => {
                this.$http.put('/api/v1/admin/notifications', { message, isShown })
                    .catch(this.Messages.showError);
            });
    }
}
