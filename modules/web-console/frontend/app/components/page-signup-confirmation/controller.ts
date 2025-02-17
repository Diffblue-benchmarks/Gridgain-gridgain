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

import {default as Auth} from '../../modules/user/Auth.service';
import {default as MessagesFactory} from '../../services/Messages.service';

export default class PageSignupConfirmation {
    email: string;

    static $inject = ['Auth', 'IgniteMessages', '$element'];

    constructor(private auth: Auth, private messages: ReturnType<typeof MessagesFactory>, private el: JQLite) {
    }

    $postLink() {
        this.el.addClass('public-page');
    }

    async resendConfirmation() {
        try {
            await this.auth.resendSignupConfirmation(this.email);
            this.messages.showInfo('Signup confirmation sent, check your email');
        }
        catch (e) {
            this.messages.showError(e);
        }
    }
}
