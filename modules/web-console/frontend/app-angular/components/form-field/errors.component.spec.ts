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

import 'app/../test/angular-testbed-init';
import {FormFieldErrors} from './errors.component';
import {assert} from 'chai';
import {TestBed, async, ComponentFixture, tick, fakeAsync} from '@angular/core/testing';
import {Component, NO_ERRORS_SCHEMA, Input, ViewChild, ElementRef} from '@angular/core';

suite('Angular form-field-errors component', () => {
    let fixture: ComponentFixture<HostComponent>;

    @Component({
        template: `
            <form-field-errors
                errorStyle='inline'
                errorType='required'
                #inline
            ></form-field-errors>
            <form-field-errors
                errorStyle='icon'
                [errorType]='errorType'
                [extraErrorMessages]='{unique: unique}'
                #icon
            ></form-field-errors>
            <ng-template #unique>Value should be unique</ng-template>
        `
    })
    class HostComponent {
        @ViewChild('inline', {read: ElementRef})
        inline: HTMLElement
        @ViewChild('icon', {read: ElementRef})
        icon: HTMLElement
        errorType = 'unique'
    }

    setup(fakeAsync(async() => {
        await TestBed.configureTestingModule({
            declarations: [
                FormFieldErrors,
                HostComponent
            ],
            schemas: [NO_ERRORS_SCHEMA]
        }).compileComponents();
        fixture = TestBed.createComponent(HostComponent);
        fixture.detectChanges();
    }));
    test('Error style', () => {
        assert.ok(
            fixture.debugElement.componentInstance.inline.nativeElement.querySelector('.inline'),
            'It can show inline errors'
        );
        assert.ok(
            fixture.debugElement.componentInstance.icon.nativeElement.querySelector('.icon'),
            'It can show icon errors'
        );
    });
    test('Validation message', () => {
        assert.equal(
            'Value is required',
            fixture.debugElement.componentInstance.inline.nativeElement.textContent,
            'It shows default message'
        );
        assert.equal(
            'Value should be unique',
            fixture.debugElement.componentInstance.icon.nativeElement.textContent,
            'It shows custom message'
        );
        fixture.componentInstance.errorType = 'foo';
        fixture.detectChanges();
        assert.equal(
            'Value is invalid: foo',
            fixture.debugElement.componentInstance.icon.nativeElement.textContent,
            'It shows placeholder message'
        );
    });
});
