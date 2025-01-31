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

import VersionService from './Version.service';

const INSTANCE = new VersionService();

import { suite, test } from 'mocha';
import { assert } from 'chai';

suite('VersionServiceTestsSuite', () => {
    test('Parse 1.7.0-SNAPSHOT', () => {
        const version = INSTANCE.parse('1.7.0-SNAPSHOT');
        assert.equal(version.major, 1);
        assert.equal(version.minor, 7);
        assert.equal(version.maintenance, 0);
        assert.equal(version.stage, 'SNAPSHOT');
        assert.equal(version.revTs, 0);
        assert.isNull(version.revHash);
    });

    test('Parse strip -DEV 1.7.0-DEV', () => {
        const version = INSTANCE.parse('1.7.0-DEV');
        assert.equal(version.major, 1);
        assert.equal(version.minor, 7);
        assert.equal(version.maintenance, 0);
        assert.equal(version.stage, '');
    });

    test('Parse strip -n/a 1.7.0-n/a', () => {
        const version = INSTANCE.parse('1.7.0-n/a');
        assert.equal(version.major, 1);
        assert.equal(version.minor, 7);
        assert.equal(version.maintenance, 0);
        assert.equal(version.stage, '');
    });

    test('Check patch version', () => {
        assert.equal(INSTANCE.compare(INSTANCE.parse('1.7.2'), INSTANCE.parse('1.7.1')), 1);
    });

    test('Check minor version', () => {
        assert.equal(INSTANCE.compare(INSTANCE.parse('1.8.1'), INSTANCE.parse('1.7.1')), 1);
    });

    test('Check major version', () => {
        assert.equal(INSTANCE.compare(INSTANCE.parse('2.7.1'), INSTANCE.parse('1.7.1')), 1);
    });

    test('Version a > b', () => {
        assert.equal(INSTANCE.compare(INSTANCE.parse('1.7.0'), INSTANCE.parse('1.5.0')), 1);
    });

    test('Version a = b', () => {
        assert.equal(INSTANCE.compare(INSTANCE.parse('1.0.0'), INSTANCE.parse('1.0.0')), 0);
        assert.equal(INSTANCE.compare(INSTANCE.parse('1.2.0'), INSTANCE.parse('1.2.0')), 0);
        assert.equal(INSTANCE.compare(INSTANCE.parse('1.2.3'), INSTANCE.parse('1.2.3')), 0);

        assert.equal(INSTANCE.compare(INSTANCE.parse('1.0.0-1'), INSTANCE.parse('1.0.0-1')), 0);
        assert.equal(INSTANCE.compare(INSTANCE.parse('1.2.0-1'), INSTANCE.parse('1.2.0-1')), 0);
        assert.equal(INSTANCE.compare(INSTANCE.parse('1.2.3-1'), INSTANCE.parse('1.2.3-1')), 0);
    });

    test('Version a < b', () => {
        assert.equal(INSTANCE.compare(INSTANCE.parse('1.5.1'), INSTANCE.parse('1.5.2')), -1);
    });

    test('Check since call', () => {
        assert.equal(INSTANCE.since('1.5.0', '1.5.0'), true);
        assert.equal(INSTANCE.since('1.6.0', '1.5.0'), true);
        assert.equal(INSTANCE.since('1.5.4', ['1.5.5', '1.6.0'], ['1.6.2']), false);
        assert.equal(INSTANCE.since('1.5.5', ['1.5.5', '1.6.0'], ['1.6.2']), true);
        assert.equal(INSTANCE.since('1.5.11', ['1.5.5', '1.6.0'], ['1.6.2']), true);
        assert.equal(INSTANCE.since('1.6.0', ['1.5.5', '1.6.0'], ['1.6.2']), false);
        assert.equal(INSTANCE.since('1.6.1', ['1.5.5', '1.6.0'], '1.6.2'), false);
        assert.equal(INSTANCE.since('1.6.2', ['1.5.5', '1.6.0'], ['1.6.2']), true);
        assert.equal(INSTANCE.since('1.6.3', ['1.5.5', '1.6.0'], '1.6.2'), true);
    });

    test('Check wrong since call', () => {
        assert.equal(INSTANCE.since('1.3.0', '1.5.0'), false);
    });

    test('Check before call', () => {
        assert.equal(INSTANCE.before('1.5.0', '1.5.0'), false);
        assert.equal(INSTANCE.before('1.5.0', '1.6.0'), true);
    });

    test('Check wrong before call', () => {
        assert.equal(INSTANCE.before('1.5.0', '1.3.0'), false);
    });
});
