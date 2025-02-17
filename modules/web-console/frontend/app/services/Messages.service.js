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

import {CancellationError} from 'app/errors/CancellationError';

/**
 * Service to show various information and error messages.
 * @param {mgcrea.ngStrap.alert.IAlertService} $alert
 * @param {import('./ErrorParser.service').default} errorParser
 */
export default function factory($alert, errorParser) {
    // Common instance of alert modal.
    let msgModal;

    const errorMessage = (prefix, err) => {
        return errorParser.extractMessage(err, prefix);
    };

    const hideAlert = () => {
        if (msgModal) {
            msgModal.hide();
            msgModal.destroy();
            msgModal = null;
        }
    };

    const _showMessage = (message, err, type, duration) => {
        hideAlert();

        const title = err ? errorMessage(message, err) : errorMessage(null, message);

        msgModal = $alert({type, title, duration});

        msgModal.$scope.icon = `icon-${type}`;
    };

    return {
        errorMessage,
        hideAlert,
        /**
         * @param {string|CancellationError} message
         * @param [err]
         */
        showError(message, err, duration = 10) {
            if (message instanceof CancellationError)
                return false;

            _showMessage(message, err, 'danger', duration);

            return false;
        },
        /**
         * @param {string} message
         */
        showInfo(message, duration = 5) {
            _showMessage(message, null, 'success', duration);
        }
    };
}

factory.$inject = ['$alert', 'IgniteErrorParser'];
