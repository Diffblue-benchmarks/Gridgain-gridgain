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

#ifndef _IGNITE_IMPL_IGNITE_ENVIRONMENT
#define _IGNITE_IMPL_IGNITE_ENVIRONMENT

#include <ignite/common/concurrent.h>
#include <ignite/jni/java.h>
#include <ignite/jni/utils.h>
#include <ignite/ignite_configuration.h>

#include <ignite/impl/interop/interop_memory.h>
#include <ignite/impl/binary/binary_type_manager.h>
#include <ignite/impl/handle_registry.h>

namespace ignite
{
    namespace impl
    {
        /* Forward declarations. */
        class IgniteBindingImpl;
        class ModuleManager;

        /**
         * Defines environment in which Ignite operates.
         */
        class IGNITE_IMPORT_EXPORT IgniteEnvironment
        {
        public:
            /**
             * Default memory block allocation size.
             */
            enum { DEFAULT_ALLOCATION_SIZE = 1024 };

            /**
             * Default fast path handle registry containers capasity.
             */
            enum { DEFAULT_FAST_PATH_CONTAINERS_CAP = 1024 };

            /**
            * Default slow path handle registry containers capasity.
            */
            enum { DEFAULT_SLOW_PATH_CONTAINERS_CAP = 16 };

            /**
             * Constructor.
             *
             * @param cfg Node configuration.
             */
            IgniteEnvironment(const IgniteConfiguration& cfg);

            /**
             * Destructor.
             */
            ~IgniteEnvironment();

            /**
             * Get node configuration.
             *
             * @return Node configuration.
             */
            const IgniteConfiguration& GetConfiguration() const;

            /**
             * Populate callback handlers.
             *
             * @param target (current env wrapped into a shared pointer).
             * @return JNI handlers.
             */
            jni::java::JniHandlers GetJniHandlers(common::concurrent::SharedPointer<IgniteEnvironment>* target);

            /**
             * Set context.
             *
             * @param ctx Context.
             */
            void SetContext(common::concurrent::SharedPointer<jni::java::JniContext> ctx);

            /**
             * Perform initialization on successful start.
             */
            void Initialize();

            /**
             * Start callback.
             *
             * @param memPtr Memory pointer.
             * @param proc Processor instance.
             */
            void OnStartCallback(long long memPtr, jobject proc);

            /**
             * Continuous query listener apply callback.
             *
             * @param mem Memory with data.
             */
            void OnContinuousQueryListenerApply(common::concurrent::SharedPointer<interop::InteropMemory>& mem);

            /**
             * Continuous query filter create callback.
             *
             * @param mem Memory with data.
             * @return Filter handle.
             */
            int64_t OnContinuousQueryFilterCreate(common::concurrent::SharedPointer<interop::InteropMemory>& mem);

            /**
             * Continuous query filter apply callback.
             *
             * @param mem Memory with data.
             */
            int64_t OnContinuousQueryFilterApply(common::concurrent::SharedPointer<interop::InteropMemory>& mem);

            /**
             * Cache Invoke callback.
             *
             * @param mem Input-output memory.
             */
            void CacheInvokeCallback(common::concurrent::SharedPointer<interop::InteropMemory>& mem);

            /**
             * Get name of Ignite instance.
             *
             * @return Name.
             */
            const char* InstanceName() const;

            /**
             * Get processor associated with the instance.
             *
             * @return Processor.
             */
            void* GetProcessor();

            /**
             * Get JNI context.
             *
             * @return Context.
             */
            jni::java::JniContext* Context();

            /**
             * Get memory for interop operations.
             *
             * @return Memory.
             */
            common::concurrent::SharedPointer<interop::InteropMemory> AllocateMemory();

            /**
             * Get memory chunk for interop operations with desired capacity.
             *
             * @param cap Capacity.
             * @return Memory.
             */
            common::concurrent::SharedPointer<interop::InteropMemory> AllocateMemory(int32_t cap);

            /**
             * Get memory chunk located at the given pointer.
             *
             * @param memPtr Memory pointer.
             * @retrun Memory.
             */
            common::concurrent::SharedPointer<interop::InteropMemory> GetMemory(int64_t memPtr);

            /**
             * Get type manager.
             *
             * @return Type manager.
             */
            binary::BinaryTypeManager* GetTypeManager();

            /**
             * Get type updater.
             *
             * @return Type updater.
             */
            binary::BinaryTypeUpdater* GetTypeUpdater();

            /**
             * Notify processor that Ignite instance has started.
             */
            void ProcessorReleaseStart();

            /**
             * Get handle registry.
             *
             * @return Handle registry.
             */
            HandleRegistry& GetHandleRegistry();

            /**
             * Get binding.
             *
             * @return IgniteBinding instance.
             */
            common::concurrent::SharedPointer<IgniteBindingImpl> GetBinding() const;

            /**
             * Get processor compute.
             *
             * @param proj Projection.
             * @return Processor compute.
             */
            jobject GetProcessorCompute(jobject proj);

            /**
             * Locally execute compute job.
             *
             * @param jobHandle Job handle.
             */
            void ComputeJobExecuteLocal(int64_t jobHandle);

            /**
             * Locally commit job execution result for the task.
             *
             * @param taskHandle Task handle.
             * @param jobHandle Job handle.
             * @return Reduce politics.
             */
            int32_t ComputeTaskLocalJobResult(int64_t taskHandle, int64_t jobHandle);

            /**
             * Reduce compute task.
             *
             * @param taskHandle Task handle.
             */
            void ComputeTaskReduce(int64_t taskHandle);

            /**
             * Complete compute task.
             *
             * @param taskHandle Task handle.
             */
            void ComputeTaskComplete(int64_t taskHandle);

            /**
             * Create compute job.
             *
             * @param mem Memory.
             * @return Job handle.
             */
            int64_t ComputeJobCreate(common::concurrent::SharedPointer<interop::InteropMemory>& mem);

            /**
             * Execute compute job.
             *
             * @param mem Memory.
             * @return Job handle.
             */
            void ComputeJobExecute(common::concurrent::SharedPointer<interop::InteropMemory>& mem);

            /**
             * Destroy compute job.
             *
             * @param jobHandle Job handle to destroy.
             */
            void ComputeJobDestroy(int64_t jobHandle);

            /**
             * Consume result of remote job execution.
             *
             * @param mem Memory containing result.
             * @return Reduce policy.
             */
            int32_t ComputeTaskJobResult(common::concurrent::SharedPointer<interop::InteropMemory>& mem);

        private:
            /** Node configuration. */
            IgniteConfiguration* cfg;

            /** Context to access Java. */
            common::concurrent::SharedPointer<jni::java::JniContext> ctx;

            /** Startup latch. */
            common::concurrent::SingleLatch latch;

            /** Ignite name. */
            char* name;

            /** Processor instance. */
            jni::JavaGlobalRef proc;

            /** Handle registry. */
            HandleRegistry registry;

            /** Type manager. */
            binary::BinaryTypeManager* metaMgr;

            /** Type updater. */
            binary::BinaryTypeUpdater* metaUpdater;

            /** Ignite binding */
            common::concurrent::SharedPointer<IgniteBindingImpl> binding;

            /** Module manager. */
            common::concurrent::SharedPointer<ModuleManager> moduleMgr;

            IGNITE_NO_COPY_ASSIGNMENT(IgniteEnvironment);
        };
    }
}

#endif //_IGNITE_IMPL_IGNITE_ENVIRONMENT