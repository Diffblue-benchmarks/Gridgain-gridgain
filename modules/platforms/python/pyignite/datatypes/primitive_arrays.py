#                   GridGain Community Edition Licensing
#                   Copyright 2019 GridGain Systems, Inc.
#
# Licensed under the Apache License, Version 2.0 (the "License") modified with Commons Clause
# Restriction; you may not use this file except in compliance with the License. You may obtain a
# copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software distributed under the
# License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied. See the License for the specific language governing permissions
# and limitations under the License.
#
# Commons Clause Restriction
#
# The Software is provided to you by the Licensor under the License, as defined below, subject to
# the following condition.
#
# Without limiting other conditions in the License, the grant of rights under the License will not
# include, and the License does not grant to you, the right to Sell the Software.
# For purposes of the foregoing, “Sell” means practicing any or all of the rights granted to you
# under the License to provide to third parties, for a fee or other consideration (including without
# limitation fees for hosting or consulting/ support services related to the Software), a product or
# service whose value derives, entirely or substantially, from the functionality of the Software.
# Any license notice or attribution required by the License must also include this Commons Clause
# License Condition notice.
#
# For purposes of the clause above, the “Licensor” is Copyright 2019 GridGain Systems, Inc.,
# the “License” is the Apache License, Version 2.0, and the Software is the GridGain Community
# Edition software provided with this notice.

import ctypes

from pyignite.constants import *
from .base import IgniteDataType
from .primitive import *
from .type_codes import *


__all__ = [
    'ByteArray', 'ByteArrayObject', 'ShortArray', 'ShortArrayObject',
    'IntArray', 'IntArrayObject', 'LongArray', 'LongArrayObject',
    'FloatArray', 'FloatArrayObject', 'DoubleArray', 'DoubleArrayObject',
    'CharArray', 'CharArrayObject', 'BoolArray', 'BoolArrayObject',
]


class PrimitiveArray(IgniteDataType):
    """
    Base class for array of primitives. Payload-only.
    """
    primitive_type = None
    type_code = None

    @classmethod
    def build_header_class(cls):
        return type(
            cls.__name__+'Header',
            (ctypes.LittleEndianStructure,),
            {
                '_pack_': 1,
                '_fields_': [
                    ('length', ctypes.c_int),
                ],
            }
        )

    @classmethod
    def parse(cls, client: 'Client'):
        header_class = cls.build_header_class()
        buffer = client.recv(ctypes.sizeof(header_class))
        header = header_class.from_buffer_copy(buffer)
        final_class = type(
            cls.__name__,
            (header_class,),
            {
                '_pack_': 1,
                '_fields_': [
                    ('data', cls.primitive_type.c_type * header.length),
                ],
            }
        )
        buffer += client.recv(
            ctypes.sizeof(final_class) - ctypes.sizeof(header_class)
        )
        return final_class, buffer

    @classmethod
    def to_python(cls, ctype_object, *args, **kwargs):
        result = []
        for i in range(ctype_object.length):
            result.append(ctype_object.data[i])
        return result

    @classmethod
    def from_python(cls, value):
        header_class = cls.build_header_class()
        header = header_class()
        if hasattr(header, 'type_code'):
            header.type_code = int.from_bytes(
                cls.type_code,
                byteorder=PROTOCOL_BYTE_ORDER
            )
        length = len(value)
        header.length = length
        buffer = bytes(header)

        for x in value:
            buffer += cls.primitive_type.from_python(x)
        return buffer


class ByteArray(PrimitiveArray):
    primitive_type = Byte
    type_code = TC_BYTE_ARRAY


class ShortArray(PrimitiveArray):
    primitive_type = Short
    type_code = TC_SHORT_ARRAY


class IntArray(PrimitiveArray):
    primitive_type = Int
    type_code = TC_INT_ARRAY


class LongArray(PrimitiveArray):
    primitive_type = Long
    type_code = TC_LONG_ARRAY


class FloatArray(PrimitiveArray):
    primitive_type = Float
    type_code = TC_FLOAT_ARRAY


class DoubleArray(PrimitiveArray):
    primitive_type = Double
    type_code = TC_DOUBLE_ARRAY


class CharArray(PrimitiveArray):
    primitive_type = Char
    type_code = TC_CHAR_ARRAY


class BoolArray(PrimitiveArray):
    primitive_type = Bool
    type_code = TC_BOOL_ARRAY


class PrimitiveArrayObject(PrimitiveArray):
    """
    Base class for primitive array object. Type code plus payload.
    """
    pythonic = list
    default = []

    @classmethod
    def build_header_class(cls):
        return type(
            cls.__name__+'Header',
            (ctypes.LittleEndianStructure,),
            {
                '_pack_': 1,
                '_fields_': [
                    ('type_code', ctypes.c_byte),
                    ('length', ctypes.c_int),
                ],
            }
        )


class ByteArrayObject(PrimitiveArrayObject):
    primitive_type = Byte
    type_code = TC_BYTE_ARRAY


class ShortArrayObject(PrimitiveArrayObject):
    primitive_type = Short
    type_code = TC_SHORT_ARRAY


class IntArrayObject(PrimitiveArrayObject):
    primitive_type = Int
    type_code = TC_INT_ARRAY


class LongArrayObject(PrimitiveArrayObject):
    primitive_type = Long
    type_code = TC_LONG_ARRAY


class FloatArrayObject(PrimitiveArrayObject):
    primitive_type = Float
    type_code = TC_FLOAT_ARRAY


class DoubleArrayObject(PrimitiveArrayObject):
    primitive_type = Double
    type_code = TC_DOUBLE_ARRAY


class CharArrayObject(PrimitiveArrayObject):
    primitive_type = Char
    type_code = TC_CHAR_ARRAY

    @classmethod
    def to_python(cls, ctype_object, *args, **kwargs):
        values = super().to_python(ctype_object, *args, **kwargs)
        return [
            v.to_bytes(
                ctypes.sizeof(cls.primitive_type.c_type),
                byteorder=PROTOCOL_BYTE_ORDER
            ).decode(
                PROTOCOL_CHAR_ENCODING
            ) for v in values
        ]


class BoolArrayObject(PrimitiveArrayObject):
    primitive_type = Bool
    type_code = TC_BOOL_ARRAY
