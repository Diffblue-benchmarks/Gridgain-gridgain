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

#ifndef _IGNITE_COMMON_CONCURRENT
#define _IGNITE_COMMON_CONCURRENT

#include <cassert>
#include <utility>

#include "ignite/common/concurrent_os.h"

namespace ignite
{
    namespace common
    {
        namespace concurrent
        {
            /**
             * Type tag for static pointer cast.
             */
            struct StaticTag {};

            /**
             * Default deleter implementation.
             *
             * @param obj Object to be deleted.
             */
            template<typename T>
            IGNITE_IMPORT_EXPORT void SharedPointerDefaultDeleter(T* obj)
            {
                delete obj;
            }

            /**
             * Holder of shared pointer data.
             */
            class IGNITE_IMPORT_EXPORT SharedPointerImpl
            {
            public:
                typedef void(*DeleterType)(void*);
                /**
                 * Constructor.
                 *
                 * @param ptr Raw pointer.
                 */
                SharedPointerImpl(void* ptr, DeleterType deleter);

                /**
                 * Get raw pointer.
                 *
                 * @return Raw pointer.
                 */
                void* Pointer();

                /**
                 * Get raw pointer.
                 *
                 * @return Raw pointer.
                 */
                const void* Pointer() const;

                /**
                 * Get raw pointer.
                 *
                 * @return Raw pointer.
                 */
                DeleterType Deleter();

                /**
                 * Increment usage counter.
                 */
                void Increment();

                /**
                 * Decrement usage counter.
                 *
                 * @return True if counter reached zero.
                 */
                bool Decrement();
            private:
                /** Raw pointer. */
                void* ptr;

                /** Deleter. */
                DeleterType deleter;

                /** Reference count. */
                int32_t refCnt;

                IGNITE_NO_COPY_ASSIGNMENT(SharedPointerImpl)
            };

            /* Forward declaration. */
            template<typename T>
            class IGNITE_IMPORT_EXPORT EnableSharedFromThis;

            /* Forward declaration. */
            template<typename T>
            inline void ImplEnableShared(EnableSharedFromThis<T>* some, SharedPointerImpl* impl);

            // Do nothing if the instance is not derived from EnableSharedFromThis.
            inline void ImplEnableShared(const volatile void*, const volatile void*)
            {
                // No-op.
            }

            /**
             * Shared pointer.
             */
            template<typename T>
            class IGNITE_IMPORT_EXPORT SharedPointer
            {
            public:
                friend class EnableSharedFromThis<T>;

                template<typename T2>
                friend class SharedPointer;

                /**
                 * Constructor.
                 */
                SharedPointer() :
                    ptr(0),
                    impl(0)
                {
                    // No-op.
                }

                /**
                 * Constructor.
                 *
                 * @param ptr Raw pointer.
                 * @param deleter Delete function.
                 */
                SharedPointer(T* ptr, void(*deleter)(T*) = &SharedPointerDefaultDeleter<T>) :
                    ptr(ptr),
                    impl(0)
                {
                    if (ptr)
                    {
                        impl = new SharedPointerImpl(ptr, reinterpret_cast<SharedPointerImpl::DeleterType>(deleter));
                        ImplEnableShared(ptr, impl);
                    }
                }

                /**
                 * Constructor.
                 *
                 * @param ptr Raw pointer.
                 * @param deleter Delete function.
                 */
                template<typename T2>
                SharedPointer(T2* ptr, void(*deleter)(T2*) = &SharedPointerDefaultDeleter<T2>) :
                    ptr(ptr),
                    impl(0)
                {
                    if (ptr)
                    {
                        impl = new SharedPointerImpl(ptr, reinterpret_cast<SharedPointerImpl::DeleterType>(deleter));
                        ImplEnableShared(ptr, impl);
                    }
                }

                /**
                 * Copy constructor.
                 *
                 * @param other Instance to copy.
                 */
                SharedPointer(const SharedPointer& other) :
                    ptr(other.ptr),
                    impl(other.impl)
                {
                    if (impl)
                        impl->Increment();
                }

                /**
                 * Copy constructor.
                 *
                 * @param other Instance to copy.
                 */
                template<typename T2>
                SharedPointer(const SharedPointer<T2>& other) :
                    ptr(other.ptr),
                    impl(other.impl)
                {
                    if (impl)
                        impl->Increment();
                }

                /**
                 * Static-cast constructor.
                 *
                 * @param other Instance to copy.
                 */
                template<typename T2>
                SharedPointer(const SharedPointer<T2>& other, StaticTag) :
                    ptr(static_cast<T*>(other.ptr)),
                    impl(other.impl)
                {
                    if (impl)
                        impl->Increment();
                }

                /**
                 * Assignment operator.
                 *
                 * @param other Other instance.
                 */
                SharedPointer& operator=(const SharedPointer& other)
                {
                    if (this != &other)
                    {
                        SharedPointer tmp(other);

                        Swap(tmp);
                    }

                    return *this;
                }

                /**
                 * Assignment operator.
                 *
                 * @param other Other instance.
                 */
                template<typename T2>
                SharedPointer& operator=(const SharedPointer<T2>& other)
                {
                    SharedPointer<T> tmp(other);

                    Swap(tmp);

                    return *this;
                }

                /**
                 * Destructor.
                 */
                ~SharedPointer()
                {
                    if (impl && impl->Decrement())
                    {
                        void* ptr0 = impl->Pointer();

                        void(*deleter)(void*) = impl->Deleter();

                        deleter(ptr0);

                        delete impl;

                        ptr = 0;
                    }
                }

                /**
                 * Get raw pointer.
                 *
                 * @return Raw pointer.
                 */
                T* Get()
                {
                    return ptr;
                }

                /**
                 * Get raw pointer.
                 *
                 * @return Raw pointer.
                 */
                const T* Get() const
                {
                    return ptr;
                }

                /**
                 * Check whether underlying raw pointer is valid.
                 *
                 * Invalid instance can be returned if some of the previous
                 * operations have resulted in a failure. For example invalid
                 * instance can be returned by not-throwing version of method
                 * in case of error. Invalid instances also often can be
                 * created using default constructor.
                 *
                 * @return True if valid.
                 */
                bool IsValid() const
                {
                    return impl != 0;
                }

                /**
                 * Swap pointer content with another instance.
                 *
                 * @param other Other instance.
                 */
                void Swap(SharedPointer& other)
                {
                    if (this != &other)
                    {
                        T* ptrTmp = ptr;
                        SharedPointerImpl* implTmp = impl;

                        ptr = other.ptr;
                        impl = other.impl;

                        other.ptr = ptrTmp;
                        other.impl = implTmp;
                    }
                }

            private:
                /* Pointer. */
                T* ptr;

                /** Implementation. */
                SharedPointerImpl* impl;
            };

            /**
             * Enables static-cast semantics for SharedPointer.
             *
             * @param val Value to cast.
             */
            template<class T1, class T2>
            SharedPointer<T1> StaticPointerCast(const SharedPointer<T2>& val)
            {
                return SharedPointer<T1>(val, StaticTag());
            }

            /**
             * The class provides functionality that allows objects of derived
             * classes to create instances of shared_ptr pointing to themselves
             * and sharing ownership with existing shared_ptr objects.
             */
            template<typename T>
            class IGNITE_IMPORT_EXPORT EnableSharedFromThis
            {
            public:
                /**
                 * Default constructor.
                 */
                EnableSharedFromThis() : self(0)
                {
                    // No-op.
                }

                /**
                 * Copy constructor.
                 */
                EnableSharedFromThis(const EnableSharedFromThis&) : self(0)
                {
                    // No-op.
                }

                /**
                 * Assignment operator.
                 */
                EnableSharedFromThis& operator=(const EnableSharedFromThis&)
                {
                    return *this;
                }

                /**
                 * Destructor.
                 */
                virtual ~EnableSharedFromThis()
                {
                    // No-op.
                }

                /**
                 * Create shared pointer for this instance.
                 *
                 * Can only be called on already shared object.
                 * @return New shared pointer instance.
                 */
                SharedPointer<T> SharedFromThis()
                {
                    assert(self != 0);

                    SharedPointer<T> ptr;

                    ptr.impl = self;

                    self->Increment();

                    return ptr;
                }

            private:
                template<typename T0>
                friend void ImplEnableShared(EnableSharedFromThis<T0>*, SharedPointerImpl*);

                /** Shared pointer base. */
                SharedPointerImpl* self;
            };

            // Implementation for instances derived from EnableSharedFromThis.
            template<typename T>
            inline void ImplEnableShared(EnableSharedFromThis<T>* some, SharedPointerImpl* impl)
            {
                if (some)
                    some->self = impl;
            }

            /**
             * Lock guard.
             */
            template<typename T>
            class LockGuard
            {
            public:
                /**
                 * Constructor.
                 *
                 * @param lock Lockable object.
                 */
                LockGuard(T& lock) :
                    lock(&lock)
                {
                    lock.Enter();
                }

                /**
                 * Destructor.
                 */
                ~LockGuard()
                {
                    if (lock)
                        lock->Leave();
                }

                /**
                 * Releases control over lock without unlocking it.
                 */
                void Forget()
                {
                    lock = 0;
                }

                /**
                 * Releases control over lock and unlocks it as if it would
                 * go out of scope.
                 */
                void Reset()
                {
                    if (lock)
                    {
                        lock->Leave();

                        Forget();
                    }
                }

            private:
                T* lock;
            };

            typedef LockGuard<CriticalSection> CsLockGuard;
        }
    }
}

#endif //_IGNITE_COMMON_CONCURRENT
