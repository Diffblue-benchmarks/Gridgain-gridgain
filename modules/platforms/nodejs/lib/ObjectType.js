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

'use strict';

const Util = require('util');
const Errors = require('./Errors');
const ArgumentChecker = require('./internal/ArgumentChecker');

/**
 * Supported Ignite type codes for primitive (simple) types.
 * @typedef ObjectType.PRIMITIVE_TYPE
 * @enum
 * @readonly
 * @property BYTE 1
 * @property SHORT 2
 * @property INTEGER 3
 * @property LONG 4
 * @property FLOAT 5
 * @property DOUBLE 6
 * @property CHAR 7
 * @property BOOLEAN 8
 * @property STRING 9
 * @property UUID 10
 * @property DATE 11
 * @property BYTE_ARRAY 12
 * @property SHORT_ARRAY 13
 * @property INTEGER_ARRAY 14
 * @property LONG_ARRAY 15
 * @property FLOAT_ARRAY 16
 * @property DOUBLE_ARRAY 17
 * @property CHAR_ARRAY 18
 * @property BOOLEAN_ARRAY 19
 * @property STRING_ARRAY 20
 * @property UUID_ARRAY 21
 * @property DATE_ARRAY 22
 * @property ENUM 28
 * @property ENUM_ARRAY 29
 * @property DECIMAL 30
 * @property DECIMAL_ARRAY 31
 * @property TIMESTAMP 33
 * @property TIMESTAMP_ARRAY 34
 * @property TIME 36
 * @property TIME_ARRAY 37
 */
const PRIMITIVE_TYPE = Object.freeze({
    BYTE : 1,
    SHORT : 2,
    INTEGER : 3,
    LONG : 4,
    FLOAT : 5,
    DOUBLE : 6,
    CHAR : 7,
    BOOLEAN : 8,
    STRING : 9,
    UUID : 10,
    DATE : 11,
    BYTE_ARRAY : 12,
    SHORT_ARRAY : 13,
    INTEGER_ARRAY : 14,
    LONG_ARRAY : 15,
    FLOAT_ARRAY : 16,
    DOUBLE_ARRAY : 17,
    CHAR_ARRAY : 18,
    BOOLEAN_ARRAY : 19,
    STRING_ARRAY : 20,
    UUID_ARRAY : 21,
    DATE_ARRAY : 22,
    ENUM : 28,
    ENUM_ARRAY : 29,
    DECIMAL : 30,
    DECIMAL_ARRAY : 31,
    TIMESTAMP : 33,
    TIMESTAMP_ARRAY : 34,
    TIME : 36,
    TIME_ARRAY : 37
});

/**
 * Supported Ignite type codes for non-primitive (composite) types.
 * @typedef ObjectType.COMPOSITE_TYPE
 * @enum
 * @readonly
 * @property OBJECT_ARRAY 23
 * @property COLLECTION 24
 * @property MAP 25
 * @property NULL 101
 * @property COMPLEX_OBJECT 103
 */
const COMPOSITE_TYPE = Object.freeze({
    OBJECT_ARRAY : 23,
    COLLECTION : 24,
    MAP : 25,
    NULL : 101,
    COMPLEX_OBJECT : 103
});

/**
 * Base class representing a type of Ignite object.
 *
 * The class has no public constructor. Only subclasses may be instantiated.
 *
 * There are two groups of Ignite object types:
 *
 * - Primitive (simple) types. To fully describe such a type it is enough to specify
 * Ignite type code {@link ObjectType.PRIMITIVE_TYPE} only.
 *
 * - Non-primitive (composite) types. To fully describe such a type
 * Ignite type code {@link ObjectType.COMPOSITE_TYPE} with additional information should be specified.
 * Eg. a kind of map or a kind of collection.
 *
 * This class helps the Ignite client to make a mapping between JavaScript types
 * and types used by Ignite.
 *
 * In many methods the Ignite client does not require to directly specify a type of Ignite object.
 * In this case the Ignite client tries to make automatic mapping between JavaScript types
 * and Ignite object types according to the following mapping tables:
 *
 * ----------------------------------------------------------------------------
 *
 * DEFAULT MAPPING FROM JavaScript type TO Ignite type code.
 *
 * This mapping is used when an application does not explicitly specify an Ignite type
 * for a field and is writing data to that field.
 *
 * <pre>
 * | JavaScript type           | Ignite type code      |
 * | ------------------------- | ----------------------|
 * | number                    | DOUBLE                |
 * | boolean                   | BOOLEAN               |
 * | string                    | STRING                |
 * | Date                      | DATE                  |
 * | Timestamp*                | TIMESTAMP             |
 * | EnumItem*                 | ENUM                  |
 * | Decimal**                 | DECIMAL               |
 * | BinaryObject*             | COMPLEX_OBJECT        |
 * | Array of number           | DOUBLE_ARRAY          |
 * | Array of boolean          | BOOLEAN_ARRAY         |
 * | Array of string           | STRING_ARRAY          |
 * | Array of Date             | DATE_ARRAY            |
 * | Array of Timestamp*       | TIMESTAMP_ARRAY       |
 * | Array of EnumItem*        | ENUM_ARRAY            |
 * | Array of Decimal**        | DECIMAL_ARRAY         |
 * | Array of BinaryObject*    | OBJECT_ARRAY          |
 * | Array of any other Object | OBJECT_ARRAY          |
 * | Set                       | COLLECTION (HASH_SET) |
 * | Map                       | MAP (HASH_MAP)        |
 * | any other Object          | COMPLEX_OBJECT        |
 * </pre>
 *
 * Type of an array content is determined by the type of the first element of the array.
 * Empty array has no default mapping.
 *
 * All other JavaScript types have no default mapping.
 *
 * ----------------------------------------------------------------------------
 *
 * DEFAULT MAPPING FROM Ignite type code TO JavaScript type.
 *
 * This mapping is used when an application does not explicitly specify an Ignite type
 * for a field and is reading data from that field.
 *
 * <pre>
 * | Ignite type code             | JavaScript type                       |
 * | ---------------------------- | --------------------------------------|
 * | BYTE                         | number                                |
 * | SHORT                        | number                                |
 * | INTEGER                      | number                                |
 * | LONG                         | number                                |
 * | FLOAT                        | number                                |
 * | DOUBLE                       | number                                |
 * | DECIMAL                      | Decimal**                             |
 * | BOOLEAN                      | boolean                               |
 * | STRING                       | string                                |
 * | CHAR                         | string (one character)                |
 * | UUID                         | Array of number (16 numbers)          |
 * | DATE                         | Date                                  |
 * | TIME                         | Date                                  |
 * | TIMESTAMP                    | Timestamp*                            |
 * | ENUM                         | EnumItem*                             |
 * | COMPLEX_OBJECT               | BinaryObject*                         |
 * | BYTE_ARRAY                   | Array of number                       |
 * | SHORT_ARRAY                  | Array of number                       |
 * | INTEGER_ARRAY                | Array of number                       |
 * | LONG_ARRAY                   | Array of number                       |
 * | FLOAT_ARRAY                  | Array of number                       |
 * | DOUBLE_ARRAY                 | Array of number                       |
 * | DECIMAL_ARRAY                | Array of Decimal**                    |
 * | BOOLEAN_ARRAY                | Array of boolean                      |
 * | STRING_ARRAY                 | Array of string                       |
 * | CHAR_ARRAY                   | Array of string (one character)       |
 * | UUID_ARRAY                   | Array of Array of number (16 numbers) |
 * | DATE_ARRAY                   | Array of Date                         |
 * | TIME_ARRAY                   | Array of Date                         |
 * | TIMESTAMP_ARRAY              | Array of Timestamp*                   |
 * | ENUM_ARRAY                   | Array of EnumItem*                    |
 * | OBJECT_ARRAY                 | Array                                 |
 * | COLLECTION (USER_COL)        | Array                                 |
 * | COLLECTION (ARR_LIST)        | Array                                 |
 * | COLLECTION (LINKED_LIST)     | Array                                 |
 * | COLLECTION (SINGLETON_LIST)  | Array                                 |
 * | COLLECTION (HASH_SET)        | Set                                   |
 * | COLLECTION (LINKED_HASH_SET) | Set                                   |
 * | COLLECTION (USER_SET)        | Set                                   |
 * | MAP (HASH_MAP)               | Map                                   |
 * | MAP (LINKED_HASH_MAP)        | Map                                   |
 * | NULL                         | null                                  |
 * </pre>
 *
 * ----------------------------------------------------------------------------
 *
 * RETURNED JavaScript types WHEN READING DATA OF THE SPECIFIED Ignite type code.
 *
 * When an application explicitly specifies an Ignite type for a field
 * and is reading data from that field - the following JavaScript types
 * are returned for every concrete Ignite type code -
 *
 * SEE THE PREVIOUS TABLE with the following additional comments:
 *
 * - for COMPLEX_OBJECT the Ignite Client returns a JavaScript Object
 * which is defined by the specified {@link ComplexObjectType}.
 *
 * - the returned Map for MAP is defined by the specified {@link MapObjectType}.
 *
 * - the returned Set or Array for COLLECTION is defined by the specified {@link CollectionObjectType}.
 *
 * - the returned Array for OBJECT_ARRAY is defined by the specified {@link ObjectArrayType}.
 *
 * - NULL cannot be specified as a type of a field but JavaScript null may be returned
 * as a value of a field.
 *
 * ----------------------------------------------------------------------------
 *
 * ALLOWED JavaScript types WHEN WRITING DATA OF THE SPECIFIED Ignite type code.
 *
 * When an application explicitly specifies an Ignite type for a field
 * and is writing data to that field - the following JavaScript types
 * are allowed for every concrete Ignite type code -
 *
 * SEE THE PREVIOUS TABLE with the following additional comments:
 *
 * - for COMPLEX_OBJECT the Ignite Client allows a JavaScript Object
 * which is defined by the specified {@link ComplexObjectType}.
 *
 * - the allowed Map for MAP is defined by the specified {@link MapObjectType}.
 *
 * - the allowed Set or Array for COLLECTION is defined by the specified {@link CollectionObjectType}.
 *
 * - the allowed Array for OBJECT_ARRAY is defined by the specified {@link ObjectArrayType}.
 *
 * - NULL cannot be specified as a type of a field but JavaScript null is allowed
 * as value of a field (but not as a key/value in a cache) or as a value of Array/Set/Map element
 * for all Ignite types, except BYTE, SHORT, INTEGER, LONG, FLOAT, DOUBLE, CHAR, BOOLEAN.
 *
 * - for all *_ARRAY Ignite types an empty JavaScript Array is allowed.
 *
 * ----------------------------------------------------------------------------
 *
 * COMMENTS TO ALL TABLES
 *
 * JavaScript type - is a JavaScript primitive or a JavaScript Object
 * ({@link https://developer.mozilla.org/en-US/docs/Web/JavaScript/Data_structures})
 *
 * (*) Timestamp, EnumItem and BinaryObject - are JavaScript Objects introduced by the Ignite client.
 *
 * (**) Decimal - is an external JavaScript Object exported into the Ignite client
 * ({@link https://github.com/MikeMcl/decimal.js})
 *
 * Ignite type code - is the type code of an Ignite primitive type ({@link ObjectType.PRIMITIVE_TYPE})
 * or an Ignite composite type ({@link ObjectType.COMPOSITE_TYPE}).
 *
 * ----------------------------------------------------------------------------
 *
 * @hideconstructor
 */

class ObjectType {
    static get PRIMITIVE_TYPE() {
        return PRIMITIVE_TYPE;
    }

    static get COMPOSITE_TYPE() {
        return COMPOSITE_TYPE;
    }

    /** Private methods */

    constructor(typeCode) {
        this._typeCode = typeCode;
    }
}

/**
 * Base class representing a non-primitive (composite) type of Ignite object.
 *
 * The class has no public constructor. Only subclasses may be instantiated.
 *
 * @hideconstructor
 * @extends ObjectType
 */
class CompositeType extends ObjectType {
}

/**
 * Supported kinds of map.
 * @typedef MapObjectType.MAP_SUBTYPE
 * @enum
 * @readonly
 * @property HASH_MAP 1
 * @property LINKED_HASH_MAP 2
 */
const MAP_SUBTYPE = Object.freeze({
    HASH_MAP : 1,
    LINKED_HASH_MAP : 2
});

/**
 * Class representing a map type of Ignite object.
 *
 * It is described by COMPOSITE_TYPE.MAP {@link ObjectType.COMPOSITE_TYPE}
 * and one of {@link MapObjectType.MAP_SUBTYPE}.
 *
 * @extends CompositeType
 */
class MapObjectType extends CompositeType {
    static get MAP_SUBTYPE() {
        return MAP_SUBTYPE;
    }

    /**
     * Public constructor.
     *
     * Optionally specifies a kind of map and types of keys and values in the map.
     *
     * If a kind of map is not specified, MAP_SUBTYPE.HASH_MAP is assumed.
     *
     * If key and/or value type is not specified then during operations the Ignite client
     * will try to make automatic mapping between JavaScript types and Ignite object types -
     * according to the mapping table defined in the description of the {@link ObjectType} class.
     *
     * @param {MapObjectType.MAP_SUBTYPE} [mapSubType=MAP_SUBTYPE.HASH_MAP] - map subtype, one of the
     *   {@link MapObjectType.MAP_SUBTYPE} constants.
     * @param {ObjectType.PRIMITIVE_TYPE | CompositeType} [keyType=null] - type of the keys in the map:
     *   - either a type code of primitive (simple) type
     *   - or an instance of class representing non-primitive (composite) type
     *   - or null (or not specified) that means the type is not specified
     * @param {ObjectType.PRIMITIVE_TYPE | CompositeType} [valueType=null] - type of the values in the map:
     *   - either a type code of primitive (simple) type
     *   - or an instance of class representing non-primitive (composite) type
     *   - or null (or not specified) that means the type is not specified
     *
     * @return {MapObjectType} - new MapObjectType instance
     *
     * @throws {IgniteClientError} if error.
     */
    constructor(mapSubType = MapObjectType.MAP_SUBTYPE.HASH_MAP, keyType = null, valueType = null) {
        super(COMPOSITE_TYPE.MAP);
        const BinaryUtils = require('./internal/BinaryUtils');
        ArgumentChecker.hasValueFrom(mapSubType, 'mapSubType', false, MapObjectType.MAP_SUBTYPE);
        this._subType = mapSubType;
        BinaryUtils.checkObjectType(keyType, 'keyType');
        BinaryUtils.checkObjectType(valueType, 'valueType');
        this._keyType = keyType;
        this._valueType = valueType;
    }
}

/**
 * Supported kinds of collections.
 * @typedef CollectionObjectType.COLLECTION_SUBTYPE
 * @enum
 * @readonly
 * @property USER_SET -1
 * @property USER_COL 0
 * @property ARRAY_LIST 1
 * @property LINKED_LIST 2
 * @property HASH_SET 3
 * @property LINKED_HASH_SET 4
 * @property SINGLETON_LIST 5
 */
const COLLECTION_SUBTYPE = Object.freeze({
    USER_SET : -1,
    USER_COL : 0,
    ARRAY_LIST : 1,
    LINKED_LIST : 2,
    HASH_SET : 3,
    LINKED_HASH_SET : 4,
    SINGLETON_LIST : 5
});

/**
 * Class representing a collection type of Ignite object.
 *
 * It is described by COMPOSITE_TYPE.COLLECTION {@link ObjectType.COMPOSITE_TYPE}
 * and one of {@link CollectionObjectType.COLLECTION_SUBTYPE}.
 *
 * @extends CompositeType
 */
class CollectionObjectType extends CompositeType {
    static get COLLECTION_SUBTYPE() {
        return COLLECTION_SUBTYPE;
    }

    /**
     * Public constructor.
     *
     * Specifies a kind of collection
     * and optionally specifies a type of elements in the collection.
     *
     * If the type of elements is not specified then during operations the Ignite client
     * will try to make automatic mapping between JavaScript types and Ignite object types -
     * according to the mapping table defined in the description of the {@link ObjectType} class.
     *
     * @param {CollectionObjectType.COLLECTION_SUBTYPE} collectionSubType - collection subtype, one of the
     *  {@link CollectionObjectType.COLLECTION_SUBTYPE} constants.
     * @param {ObjectType.PRIMITIVE_TYPE | CompositeType} [elementType=null] - type of elements in the collection:
     *   - either a type code of primitive (simple) type
     *   - or an instance of class representing non-primitive (composite) type
     *   - or null (or not specified) that means the type is not specified
     *
     * @return {CollectionObjectType} - new CollectionObjectType instance
     *
     * @throws {IgniteClientError} if error.
     */
    constructor(collectionSubType, elementType = null) {
        super(COMPOSITE_TYPE.COLLECTION);
        const BinaryUtils = require('./internal/BinaryUtils');
        ArgumentChecker.hasValueFrom(
            collectionSubType, 'collectionSubType', false, CollectionObjectType.COLLECTION_SUBTYPE);
        this._subType = collectionSubType;
        BinaryUtils.checkObjectType(elementType, 'elementType');
        this._elementType = elementType;
    }

    /** Private methods */

    /**
     * @ignore
     */
    static _isSet(subType) {
        return subType === CollectionObjectType.COLLECTION_SUBTYPE.USER_SET ||
            subType === CollectionObjectType.COLLECTION_SUBTYPE.HASH_SET ||
            subType === CollectionObjectType.COLLECTION_SUBTYPE.LINKED_HASH_SET;
    }

    /**
     * @ignore
     */
    _isSet() {
        return CollectionObjectType._isSet(this._subType);
    }
}

/**
 * Class representing an array type of Ignite objects.
 *
 * It is described by COMPOSITE_TYPE.OBJECT_ARRAY {@link ObjectType.COMPOSITE_TYPE}.
 *
 * @extends CompositeType
 */
class ObjectArrayType extends CompositeType {

    /**
     * Public constructor.
     *
     * Optionally specifies a type of elements in the array.
     *
     * If the type of elements is not specified then during operations the Ignite client
     * will try to make automatic mapping between JavaScript types and Ignite object types -
     * according to the mapping table defined in the description of the {@link ObjectType} class.
     *
     * @param {ObjectType.PRIMITIVE_TYPE | CompositeType} [elementType=null] - type of the array element:
     *   - either a type code of primitive (simple) type
     *   - or an instance of class representing non-primitive (composite) type
     *   - or null (or not specified) that means the type is not specified
     *
     * @return {ObjectArrayType} - new ObjectArrayType instance
     *
     * @throws {IgniteClientError} if error.
     */
    constructor(elementType = null) {
        super(COMPOSITE_TYPE.OBJECT_ARRAY);
        const BinaryUtils = require('./internal/BinaryUtils');
        BinaryUtils.checkObjectType(elementType, 'elementType');
        this._elementType = elementType;
    }
}

/**
 * Class representing a complex type of Ignite object.
 *
 * It is described by COMPOSITE_TYPE.COMPLEX_OBJECT {@link ObjectType.COMPOSITE_TYPE},
 * by a name of the complex type and by a JavaScript Object which is mapped to/from the Ignite complex type.
 *
 * @extends CompositeType
 */
class ComplexObjectType extends CompositeType {

    /**
     * Public constructor.
     *
     * Specifies a JavaScript Object type which will be mapped to/from the complex type.
     * This specification is done using an instance of the JavaScript Object.
     *
     * If an object of the complex type is going to be received (deserialized),
     * the JavaScript Object must have a constructor without parameters or with optional parameters only.
     *
     * The JavaScript Object defines a set of fields of the complex type.
     *
     * By default, the fields have no types specified. It means during operations the Ignite client
     * will try to make automatic mapping between JavaScript types and Ignite object types -
     * according to the mapping table defined in the description of the {@link ObjectType} class.
     *
     * A type of any field may be specified later by setFieldType() method.
     *
     * By default, the name of the complex type is the name of the JavaScript Object.
     * The name may be explicitely specified using optional typeName parameter in the constructor.
     *
     * @param {object} jsObject - instance of JavaScript Object which will be mapped to/from this complex type.
     * @param {string} [typeName] - name of the complex type.
     *
     * @return {ComplexObjectType} - new ComplexObjectType instance
     *
     * @throws {IgniteClientError} if error.
     */
    constructor(jsObject, typeName = null) {
        super(COMPOSITE_TYPE.COMPLEX_OBJECT);
        ArgumentChecker.notEmpty(jsObject, 'jsObject');
        this._template = jsObject;
        this._objectConstructor = jsObject && jsObject.constructor ?
            jsObject.constructor : Object;
        if (!typeName) {
            typeName = this._objectConstructor.name;
        }
        this._typeName = typeName;
        this._fields = new Map();
        const BinaryUtils = require('./internal/BinaryUtils');
        for (let fieldName of BinaryUtils.getJsObjectFieldNames(this._template)) {
            this._fields.set(fieldName, null);
        }
    }

    /**
     * Specifies a type of the field in the complex type.
     *
     * If the type is not specified then during operations the Ignite client
     * will try to make automatic mapping between JavaScript types and Ignite object types -
     * according to the mapping table defined in the description of the {@link ObjectType} class.
     *
     * @param {string} fieldName - name of the field.
     * @param {ObjectType.PRIMITIVE_TYPE | CompositeType} fieldType - type of the field:
     *   - either a type code of primitive (simple) type
     *   - or an instance of class representing non-primitive (composite) type
     *   - or null (means the type is not specified).
     *
     * @return {ComplexObjectType} - the same instance of the ComplexObjectType.
     *
     * @throws {IgniteClientError} if error.
     */
    setFieldType(fieldName, fieldType) {
        if (!this._fields.has(fieldName)) {
            throw Errors.IgniteClientError.illegalArgumentError(
                Util.format('Field "%s" is absent in the complex object type', fieldName));
        }
        const BinaryUtils = require('./internal/BinaryUtils');
        BinaryUtils.checkObjectType(fieldType, 'fieldType');
        this._fields.set(fieldName, fieldType);
        return this;
    }

    /** Private methods */

    /**
     * @ignore
     */
    _getFieldType(fieldName) {
        return this._fields.get(fieldName);
    }
}

module.exports.ObjectType = ObjectType;
module.exports.CompositeType = CompositeType;
module.exports.MapObjectType = MapObjectType;
module.exports.CollectionObjectType = CollectionObjectType;
module.exports.ComplexObjectType = ComplexObjectType;
module.exports.ObjectArrayType = ObjectArrayType;
