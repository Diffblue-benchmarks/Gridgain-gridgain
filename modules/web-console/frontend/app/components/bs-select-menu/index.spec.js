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

import 'mocha';
import {assert} from 'chai';
import angular from 'angular';
import componentModule from './index';

suite('bs-select-menu', () => {
    /** @type {ng.IScope} */
    let $scope;
    /** @type {ng.ICompileService} */
    let $compile;

    setup(() => {
        angular.module('test', [componentModule.name]);
        angular.mock.module('test');
        angular.mock.inject((_$rootScope_, _$compile_) => {
            $compile = _$compile_;
            $scope = _$rootScope_.$new();
        });
    });

    test('Create/destroy', () => {
        $scope.$matches = [];
        $scope.show = false;
        const el = angular.element(`
            <div ng-if='show'>
                <bs-select-menu></bs-select-menu>
            </div>
        `);

        const overlay = () => document.body.querySelector('.bssm-click-overlay');

        $compile(el)($scope);
        $scope.$digest();
        assert.notOk(overlay(), 'No overlay on init');

        $scope.show = true;
        $scope.$isShown = true;
        $scope.$digest();
        assert.ok(overlay(), 'Adds overlay to body on show');

        $scope.show = false;
        $scope.$digest();
        assert.notOk(overlay(), 'Removes overlay when element is removed from DOM');

        $scope.show = true;
        $scope.$isShown = false;
        $scope.$digest();
        assert.notOk(overlay(), 'Removes overlay menu is closed');
    });
});
