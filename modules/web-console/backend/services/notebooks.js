/*
 *                   GridGain Community Edition Licensing
 *                   Copyright 2019 GridGain Systems, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License") modified with Commons Clause
 * Restriction; you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * Commons Clause Restriction
 *
 * The Software is provided to you by the Licensor under the License, as defined below, subject to
 * the following condition.
 *
 * Without limiting other conditions in the License, the grant of rights under the License will not
 * include, and the License does not grant to you, the right to Sell the Software.
 * For purposes of the foregoing, “Sell” means practicing any or all of the rights granted to you
 * under the License to provide to third parties, for a fee or other consideration (including without
 * limitation fees for hosting or consulting/ support services related to the Software), a product or
 * service whose value derives, entirely or substantially, from the functionality of the Software.
 * Any license notice or attribution required by the License must also include this Commons Clause
 * License Condition notice.
 *
 * For purposes of the clause above, the “Licensor” is Copyright 2019 GridGain Systems, Inc.,
 * the “License” is the Apache License, Version 2.0, and the Software is the GridGain Community
 * Edition software provided with this notice.
 */

'use strict';

const _ = require('lodash');

// Fire me up!

module.exports = {
    implements: 'services/notebooks',
    inject: ['mongo', 'services/spaces', 'errors']
};

/**
 * @param mongo
 * @param {SpacesService} spacesService
 * @param errors
 * @returns {NotebooksService}
 */
module.exports.factory = (mongo, spacesService, errors) => {
    /**
     * Convert remove status operation to own presentation.
     *
     * @param {RemoveResult} result - The results of remove operation.
     */
    const convertRemoveStatus = ({result}) => ({rowsAffected: result.n});

    /**
     * Update existing notebook.
     *
     * @param {Object} notebook - The notebook for updating
     * @returns {Promise.<mongo.ObjectId>} that resolves cache id
     */
    const update = (notebook) => {
        return mongo.Notebook.findOneAndUpdate({_id: notebook._id}, notebook, {new: true, upsert: true}).exec()
            .catch((err) => {
                if (err.code === mongo.errCodes.DUPLICATE_KEY_UPDATE_ERROR || err.code === mongo.errCodes.DUPLICATE_KEY_ERROR)
                    throw new errors.DuplicateKeyException('Notebook with name: "' + notebook.name + '" already exist.');
                else
                    throw err;
            });
    };

    /**
     * Create new notebook.
     *
     * @param {Object} notebook - The notebook for creation.
     * @returns {Promise.<mongo.ObjectId>} that resolves cache id.
     */
    const create = (notebook) => {
        return mongo.Notebook.create(notebook)
            .catch((err) => {
                if (err.code === mongo.errCodes.DUPLICATE_KEY_ERROR)
                    throw new errors.DuplicateKeyException('Notebook with name: "' + notebook.name + '" already exist.');
                else
                    throw err;
            });
    };

    class NotebooksService {
        /**
         * Create or update Notebook.
         *
         * @param {Object} notebook - The Notebook
         * @returns {Promise.<mongo.ObjectId>} that resolves Notebook id of merge operation.
         */
        static merge(notebook) {
            if (notebook._id)
                return update(notebook);

            return create(notebook);
        }

        /**
         * Get notebooks by spaces.
         *
         * @param {mongo.ObjectId|String} spaceIds - The spaces ids that own caches.
         * @returns {Promise.<mongo.Notebook[]>} - contains requested caches.
         */
        static listBySpaces(spaceIds) {
            return mongo.Notebook.find({space: {$in: spaceIds}}).sort('name').lean().exec();
        }

        /**
         * Remove notebook.
         *
         * @param {mongo.ObjectId|String} notebookId - The Notebook id for remove.
         * @returns {Promise.<{rowsAffected}>} - The number of affected rows.
         */
        static remove(notebookId) {
            if (_.isNil(notebookId))
                return Promise.reject(new errors.IllegalArgumentException('Notebook id can not be undefined or null'));

            return mongo.Notebook.remove({_id: notebookId}).exec()
                .then(convertRemoveStatus);
        }
    }

    return NotebooksService;
};
