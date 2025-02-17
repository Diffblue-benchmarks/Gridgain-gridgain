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

namespace Apache.Ignite.Core.Impl.Binary
{
    using System;
    using System.Collections;
    using System.Collections.Generic;
    using System.Diagnostics;
    using System.Diagnostics.CodeAnalysis;
    using System.IO;
    using System.Runtime.CompilerServices;
    using System.Text;
    using Apache.Ignite.Core.Binary;
    using Apache.Ignite.Core.Common;
    using Apache.Ignite.Core.Impl.Binary.IO;
    using Apache.Ignite.Core.Impl.Common;

    /// <summary>
    /// Binary object.
    /// </summary>
    internal class BinaryObject : IBinaryObject
    {
        /** Cache empty dictionary. */
        private static readonly IDictionary<int, int> EmptyFields = new Dictionary<int, int>();

        /** Marshaller. */
        private readonly Marshaller _marsh;

        /** Raw data of this binary object. */
        private readonly byte[] _data;

        /** Offset in data array. */
        private readonly int _offset;

        /** Header. */
        private readonly BinaryObjectHeader _header;

        /** Fields. */
        private volatile IDictionary<int, int> _fields;

        /** Deserialized value. */
        private object _deserialized;

        /// <summary>
        /// Initializes a new instance of the <see cref="BinaryObject" /> class.
        /// </summary>
        /// <param name="marsh">Marshaller.</param>
        /// <param name="data">Raw data of this binary object.</param>
        /// <param name="offset">Offset in data array.</param>
        /// <param name="header">The header.</param>
        public BinaryObject(Marshaller marsh, byte[] data, int offset, BinaryObjectHeader header)
        {
            Debug.Assert(marsh != null);
            Debug.Assert(data != null);
            Debug.Assert(offset >= 0 && offset < data.Length);

            _marsh = marsh;

            _data = data;
            _offset = offset;

            _header = header;
        }

        /** <inheritdoc /> */
        public int TypeId
        {
            get { return _header.TypeId; }
        }

        /** <inheritdoc /> */
        public T GetField<T>(string fieldName)
        {
            IgniteArgumentCheck.NotNullOrEmpty(fieldName, "fieldName");

            int pos;

            return TryGetFieldPosition(fieldName, out pos) ? GetField<T>(pos, null) : default(T);
        }

        /** <inheritdoc /> */
        public bool HasField(string fieldName)
        {
            IgniteArgumentCheck.NotNullOrEmpty(fieldName, "fieldName");

            int pos;

            return TryGetFieldPosition(fieldName, out pos);
        }

        /// <summary>
        /// Gets field value on the given object.
        /// </summary>
        /// <param name="pos">Position.</param>
        /// <param name="builder">Builder.</param>
        /// <returns>Field value.</returns>
        public T GetField<T>(int pos, BinaryObjectBuilder builder)
        {
            using (IBinaryStream stream = new BinaryHeapStream(_data))
            {
                stream.Seek(pos + _offset, SeekOrigin.Begin);

                return _marsh.Unmarshal<T>(stream, BinaryMode.ForceBinary, builder);
            }
        }

        /** <inheritdoc /> */
        public T Deserialize<T>()
        {
            return Deserialize<T>(BinaryMode.Deserialize);
        }

        /** <inheritdoc /> */
        [ExcludeFromCodeCoverage]
        public int EnumValue
        {
            get
            {
                throw new NotSupportedException("IBinaryObject.EnumValue is only supported for enums. " +
                    "Check IBinaryObject.GetBinaryType().IsEnum property before accessing Value.");
            }
        }

        /** <inheritdoc /> */
        [ExcludeFromCodeCoverage]
        public string EnumName
        {
            get
            {
                throw new NotSupportedException("IBinaryObject.EnumName is only supported for enums. " +
                    "Check IBinaryObject.GetBinaryType().IsEnum property before accessing Value.");
            }
        }

        /** <inheritdoc /> */
        public IBinaryObjectBuilder ToBuilder()
        {
            return _marsh.Ignite.GetBinary().GetBuilder(this);
        }

        /// <summary>
        /// Internal deserialization routine.
        /// </summary>
        /// <param name="mode">The mode.</param>
        /// <returns>
        /// Deserialized object.
        /// </returns>
        private T Deserialize<T>(BinaryMode mode)
        {
            if (_deserialized == null)
            {
                T res;

                using (IBinaryStream stream = new BinaryHeapStream(_data))
                {
                    stream.Seek(_offset, SeekOrigin.Begin);

                    res = _marsh.Unmarshal<T>(stream, mode);
                }

                var desc = _marsh.GetDescriptor(true, _header.TypeId);

                if (!desc.KeepDeserialized)
                    return res;

                _deserialized = res;
            }

            return (T)_deserialized;
        }

        /** <inheritdoc /> */
        public IBinaryType GetBinaryType()
        {
            return _marsh.GetBinaryType(_header.TypeId);
        }

        /// <summary>
        /// Raw data of this binary object.
        /// </summary>
        public byte[] Data
        {
            get { return _data; }
        }

        /// <summary>
        /// Offset in data array.
        /// </summary>
        public int Offset
        {
            get { return _offset; }
        }

        /// <summary>
        /// Gets the header.
        /// </summary>
        public BinaryObjectHeader Header
        {
            get { return _header; }
        }

        public bool TryGetFieldPosition(string fieldName, out int pos)
        {
            var desc = _marsh.GetDescriptor(true, _header.TypeId);

            InitializeFields(desc);

            int fieldId = BinaryUtils.FieldId(_header.TypeId, fieldName, desc.NameMapper, desc.IdMapper);

            return _fields.TryGetValue(fieldId, out pos);
        }

        /// <summary>
        /// Lazy fields initialization routine.
        /// </summary>
        private void InitializeFields(IBinaryTypeDescriptor desc = null)
        {
            if (_fields != null) 
                return;

            desc = desc ?? _marsh.GetDescriptor(true, _header.TypeId);

            using (var stream = new BinaryHeapStream(_data))
            {
                var hdr = BinaryObjectHeader.Read(stream, _offset);

                _fields = BinaryObjectSchemaSerializer.ReadSchema(stream, _offset, hdr, desc.Schema, _marsh.Ignite)
                    .ToDictionary() ?? EmptyFields;
            }
        }

        /** <inheritdoc /> */
        public override int GetHashCode()
        {
            return _header.HashCode;
        }

        /** <inheritdoc /> */
        public override bool Equals(object obj)
        {
            if (this == obj)
                return true;

            BinaryObject that = obj as BinaryObject;

            if (that == null)
                return false;

            if (_data == that._data && _offset == that._offset)
                return true;

            if (TypeId != that.TypeId)
                return false;

            return BinaryArrayEqualityComparer.Equals(this, that);
        }

        /** <inheritdoc /> */
        public override string ToString()
        {
            return ToString(new Dictionary<int, int>());            
        }

        /// <summary>
        /// ToString implementation.
        /// </summary>
        /// <param name="handled">Already handled objects.</param>
        /// <returns>Object string.</returns>
        private string ToString(IDictionary<int, int> handled)
        {
            int idHash;

            bool alreadyHandled = handled.TryGetValue(_offset, out idHash);

            if (!alreadyHandled)
                idHash = RuntimeHelpers.GetHashCode(this);

            StringBuilder sb;

            IBinaryTypeDescriptor desc = _marsh.GetDescriptor(true, _header.TypeId);

            IBinaryType meta;

            try
            {
                meta = _marsh.GetBinaryType(_header.TypeId);
            }
            catch (IgniteException)
            {
                meta = null;
            }

            if (meta == null)
                sb = new StringBuilder("BinaryObject [typeId=").Append(_header.TypeId).Append(", idHash=" + idHash);
            else
            {
                sb = new StringBuilder(meta.TypeName).Append(" [idHash=" + idHash);

                if (!alreadyHandled)
                {
                    handled[_offset] = idHash;

                    InitializeFields();
                    
                    foreach (string fieldName in meta.Fields)
                    {
                        sb.Append(", ");

                        int fieldId = BinaryUtils.FieldId(_header.TypeId, fieldName, desc.NameMapper, desc.IdMapper);

                        int fieldPos;

                        if (_fields.TryGetValue(fieldId, out fieldPos))
                        {
                            sb.Append(fieldName).Append('=');

                            ToString0(sb, GetField<object>(fieldPos, null), handled);
                        }
                    }
                }
                else
                    sb.Append(", ...");
            }

            sb.Append(']');

            return sb.ToString();
        }

        /// <summary>
        /// Internal ToString routine with correct collections printout.
        /// </summary>
        /// <param name="sb">String builder.</param>
        /// <param name="obj">Object to print.</param>
        /// <param name="handled">Already handled objects.</param>
        /// <returns>The same string builder.</returns>
        private static void ToString0(StringBuilder sb, object obj, IDictionary<int, int> handled)
        {
            IEnumerable col = (obj is string) ? null : obj as IEnumerable;

            if (col == null)
            {
                BinaryObject obj0 = obj as BinaryObject;

                sb.Append(obj0 == null ? obj : obj0.ToString(handled));
            }
            else
            {
                sb.Append('[');

                bool first = true;

                foreach (object elem in col)
                {
                    if (first)
                        first = false;
                    else
                        sb.Append(", ");

                    ToString0(sb, elem, handled);
                }

                sb.Append(']');
            }
        }
    }
}
