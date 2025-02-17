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

import bytesFilter from './bytes.filter';

import { suite, test } from 'mocha';
import { assert } from 'chai';

const bytesFilterInstance = bytesFilter();

suite('bytes filter', () => {
    test('bytes filter', () => {
        assert.equal(bytesFilterInstance(0), '0 bytes');
        assert.equal(bytesFilterInstance(1000), '1000.0 bytes');
        assert.equal(bytesFilterInstance(1024), '1.0 kB');
        assert.equal(bytesFilterInstance(5000), '4.9 kB');
        assert.equal(bytesFilterInstance(1048576), '1.0 MB');
        assert.equal(bytesFilterInstance(104857600), '100.0 MB');
        assert.equal(bytesFilterInstance(1073741824), '1.0 GB');
        assert.equal(bytesFilterInstance(1099511627776), '1.0 TB');
    });
});
