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

/**
 * Service to transfer focus for specified element.
 * @param {ng.ITimeoutService} $timeout
 */
export default function factory($timeout) {
    return {
        /**
         * @param {string} id Element id
         */
        move(id) {
            // Timeout makes sure that is invoked after any other event has been triggered.
            // E.g. click events that need to run before the focus or inputs elements that are
            // in a disabled state but are enabled when those events are triggered.
            $timeout(() => {
                const elem = $('#' + id);

                if (elem.length > 0)
                    elem[0].focus();
            }, 100);
        }
    };
}

factory.$inject = ['$timeout'];
