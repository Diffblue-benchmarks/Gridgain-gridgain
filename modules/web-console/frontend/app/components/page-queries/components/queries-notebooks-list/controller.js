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

export class NotebooksListCtrl {
    static $inject = ['IgniteNotebook', 'IgniteMessages', 'IgniteLoading', 'IgniteInput', '$scope', '$modal'];

    constructor(IgniteNotebook, IgniteMessages, IgniteLoading, IgniteInput, $scope, $modal) {
        Object.assign(this, { IgniteNotebook, IgniteMessages, IgniteLoading, IgniteInput, $scope, $modal });

        this.notebooks = [];

        this.rowsToShow = 8;

        const notebookNameTemplate = `<div class="ui-grid-cell-contents notebook-name"><a ui-sref="base.sql.notebook({ noteId: row.entity._id })">{{ row.entity.name }}</a></div>`;
        const sqlQueryTemplate = `<div class="ui-grid-cell-contents">{{row.entity.sqlQueriesParagraphsLength}}</div>`;
        const scanQueryTemplate = `<div class="ui-grid-cell-contents">{{row.entity.scanQueriesPsaragraphsLength}}</div>`;

        this.categories = [
            { name: 'Name', visible: true, enableHiding: false },
            { name: 'SQL Queries', visible: true, enableHiding: false },
            { name: 'Scan Queries', visible: true, enableHiding: false }
        ];

        this.columnDefs = [
            { name: 'name', displayName: 'Notebook name', categoryDisplayName: 'Name', field: 'name', cellTemplate: notebookNameTemplate, filter: { placeholder: 'Filter by Name...' } },
            { name: 'sqlQueryNum', displayName: 'SQL Queries', categoryDisplayName: 'SQL Queries', field: 'sqlQueriesParagraphsLength', cellTemplate: sqlQueryTemplate, enableSorting: true, type: 'number', minWidth: 150, width: '10%', enableFiltering: false },
            { name: 'scanQueryNum', displayName: 'Scan Queries', categoryDisplayName: 'Scan Queries', field: 'scanQueriesParagraphsLength', cellTemplate: scanQueryTemplate, enableSorting: true, type: 'number', minWidth: 150, width: '10%', enableFiltering: false }
        ];

        this.actionOptions = [
            {
                action: 'Clone',
                click: this.cloneNotebook.bind(this),
                available: true
            },
            {
                action: 'Rename',
                click: this.renameNotebok.bind(this),
                available: true
            },
            {
                action: 'Delete',
                click: this.deleteNotebooks.bind(this),
                available: true
            }
        ];
    }

    $onInit() {
        this._loadAllNotebooks();
    }

    async _loadAllNotebooks() {
        try {
            this.IgniteLoading.start('notebooksLoading');

            const data = await this.IgniteNotebook.read();

            this.notebooks = this._preprocessNotebooksList(data);
        }
        catch (err) {
            this.IgniteMessages.showError(err);
        }
        finally {
            this.$scope.$applyAsync();

            this.IgniteLoading.finish('notebooksLoading');
        }
    }

    _preprocessNotebooksList(notebooks = []) {
        return notebooks.map((notebook) => {
            notebook.sqlQueriesParagraphsLength = this._countParagraphs(notebook, 'query');
            notebook.scanQueriesPsaragraphsLength = this._countParagraphs(notebook, 'scan');

            return notebook;
        });
    }

    _countParagraphs(notebook, queryType = 'query') {
        return notebook.paragraphs.filter((paragraph) => paragraph.qryType === queryType).length || 0;
    }

    onSelectionChanged() {
        this._checkActionsAllow();
    }

    _checkActionsAllow() {
        // Dissallow clone and rename if more then one item is selectted.
        const oneItemIsSelected  = this.gridApi.selection.legacyGetSelectedRows().length === 1;
        this.actionOptions[0].available = oneItemIsSelected;
        this.actionOptions[1].available = oneItemIsSelected;
    }

    async createNotebook() {
        try {
            const newNotebookName = await this.IgniteInput.input('New query notebook', 'Notebook name');

            this.IgniteLoading.start('notebooksLoading');

            await this.IgniteNotebook.create(newNotebookName);

            this.IgniteLoading.finish('notebooksLoading');

            this._loadAllNotebooks();
        }
        catch (err) {
            this.IgniteMessages.showError(err);
        }
        finally {
            this.IgniteLoading.finish('notebooksLoading');

            if (this.createNotebookModal)
                this.createNotebookModal.$promise.then(this.createNotebookModal.hide);
        }
    }

    async renameNotebok() {
        try {
            const currentNotebook = this.gridApi.selection.legacyGetSelectedRows()[0];
            const newNotebookName = await this.IgniteInput.input('Rename notebook', 'Notebook name', currentNotebook.name);

            if (this.getNotebooksNames().find((name) => newNotebookName === name))
                throw Error(`Notebook with name "${newNotebookName}" already exists!`);

            this.IgniteLoading.start('notebooksLoading');

            await this.IgniteNotebook.save(Object.assign(currentNotebook, {name: newNotebookName}));
        }
        catch (err) {
            this.IgniteMessages.showError(err);
        }
        finally {
            this.IgniteLoading.finish('notebooksLoading');

            this._loadAllNotebooks();
        }
    }

    async cloneNotebook() {
        try {
            const clonedNotebook = Object.assign({}, this.gridApi.selection.legacyGetSelectedRows()[0]);
            const newNotebookName = await this.IgniteInput.clone(clonedNotebook.name, this.getNotebooksNames());

            this.IgniteLoading.start('notebooksLoading');

            await this.IgniteNotebook.clone(newNotebookName, clonedNotebook);

            this.IgniteLoading.finish('notebooksLoading');

            this._loadAllNotebooks();
        }
        catch (err) {
            this.IgniteMessages.showError(err);
        }
        finally {
            this.IgniteLoading.finish('notebooksLoading');

            if (this.createNotebookModal)
                this.createNotebookModal.$promise.then(this.createNotebookModal.hide);
        }
    }

    getNotebooksNames() {
        return this.notebooks.map((notebook) => notebook.name);
    }

    async deleteNotebooks() {
        try {
            this.IgniteLoading.start('notebooksLoading');

            await this.IgniteNotebook.removeBatch(this.gridApi.selection.legacyGetSelectedRows());

            this.IgniteLoading.finish('notebooksLoading');

            this._loadAllNotebooks();
        }
        catch (err) {
            this.IgniteMessages.showError(err);

            this.IgniteLoading.finish('notebooksLoading');
        }
    }

    _adjustHeight(rows) {
        // Add header height.
        const height = Math.min(rows, this.rowsToShow) * 48 + 78;

        this.gridApi.grid.element.css('height', height + 'px');

        this.gridApi.core.handleWindowResize();
    }
}
