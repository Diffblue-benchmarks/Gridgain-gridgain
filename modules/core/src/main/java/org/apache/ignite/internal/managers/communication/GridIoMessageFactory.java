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

package org.apache.ignite.internal.managers.communication;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.ignite.IgniteException;
import org.apache.ignite.internal.GridJobCancelRequest;
import org.apache.ignite.internal.GridJobExecuteRequest;
import org.apache.ignite.internal.GridJobExecuteResponse;
import org.apache.ignite.internal.GridJobSiblingsRequest;
import org.apache.ignite.internal.GridJobSiblingsResponse;
import org.apache.ignite.internal.GridTaskCancelRequest;
import org.apache.ignite.internal.GridTaskSessionRequest;
import org.apache.ignite.internal.IgniteDiagnosticMessage;
import org.apache.ignite.internal.binary.BinaryEnumObjectImpl;
import org.apache.ignite.internal.binary.BinaryObjectImpl;
import org.apache.ignite.internal.managers.checkpoint.GridCheckpointRequest;
import org.apache.ignite.internal.managers.deployment.GridDeploymentInfoBean;
import org.apache.ignite.internal.managers.deployment.GridDeploymentRequest;
import org.apache.ignite.internal.managers.deployment.GridDeploymentResponse;
import org.apache.ignite.internal.managers.encryption.GenerateEncryptionKeyRequest;
import org.apache.ignite.internal.managers.encryption.GenerateEncryptionKeyResponse;
import org.apache.ignite.internal.managers.eventstorage.GridEventStorageMessage;
import org.apache.ignite.internal.processors.affinity.AffinityTopologyVersion;
import org.apache.ignite.internal.processors.authentication.UserAuthenticateRequestMessage;
import org.apache.ignite.internal.processors.authentication.UserAuthenticateResponseMessage;
import org.apache.ignite.internal.processors.authentication.UserManagementOperationFinishedMessage;
import org.apache.ignite.internal.processors.cache.CacheEntryInfoCollection;
import org.apache.ignite.internal.processors.cache.CacheEntryPredicateContainsValue;
import org.apache.ignite.internal.processors.cache.CacheEntrySerializablePredicate;
import org.apache.ignite.internal.processors.cache.CacheEvictionEntry;
import org.apache.ignite.internal.processors.cache.CacheInvokeDirectResult;
import org.apache.ignite.internal.processors.cache.CacheObjectByteArrayImpl;
import org.apache.ignite.internal.processors.cache.CacheObjectImpl;
import org.apache.ignite.internal.processors.cache.GridCacheEntryInfo;
import org.apache.ignite.internal.processors.cache.GridCacheMvccEntryInfo;
import org.apache.ignite.internal.processors.cache.GridCacheReturn;
import org.apache.ignite.internal.processors.cache.GridChangeGlobalStateMessageResponse;
import org.apache.ignite.internal.processors.cache.KeyCacheObjectImpl;
import org.apache.ignite.internal.processors.cache.WalStateAckMessage;
import org.apache.ignite.internal.processors.cache.binary.MetadataRequestMessage;
import org.apache.ignite.internal.processors.cache.binary.MetadataResponseMessage;
import org.apache.ignite.internal.processors.cache.distributed.GridCacheTtlUpdateRequest;
import org.apache.ignite.internal.processors.cache.distributed.GridCacheTxRecoveryRequest;
import org.apache.ignite.internal.processors.cache.distributed.GridCacheTxRecoveryResponse;
import org.apache.ignite.internal.processors.cache.distributed.GridDistributedLockRequest;
import org.apache.ignite.internal.processors.cache.distributed.GridDistributedLockResponse;
import org.apache.ignite.internal.processors.cache.distributed.GridDistributedTxFinishRequest;
import org.apache.ignite.internal.processors.cache.distributed.GridDistributedTxFinishResponse;
import org.apache.ignite.internal.processors.cache.distributed.GridDistributedTxPrepareRequest;
import org.apache.ignite.internal.processors.cache.distributed.GridDistributedTxPrepareResponse;
import org.apache.ignite.internal.processors.cache.distributed.GridDistributedUnlockRequest;
import org.apache.ignite.internal.processors.cache.distributed.dht.GridDhtAffinityAssignmentRequest;
import org.apache.ignite.internal.processors.cache.distributed.dht.GridDhtAffinityAssignmentResponse;
import org.apache.ignite.internal.processors.cache.distributed.dht.GridDhtLockRequest;
import org.apache.ignite.internal.processors.cache.distributed.dht.GridDhtLockResponse;
import org.apache.ignite.internal.processors.cache.distributed.dht.GridDhtTxFinishRequest;
import org.apache.ignite.internal.processors.cache.distributed.dht.GridDhtTxFinishResponse;
import org.apache.ignite.internal.processors.cache.distributed.dht.GridDhtTxOnePhaseCommitAckRequest;
import org.apache.ignite.internal.processors.cache.distributed.dht.GridDhtTxPrepareRequest;
import org.apache.ignite.internal.processors.cache.distributed.dht.GridDhtTxPrepareResponse;
import org.apache.ignite.internal.processors.cache.distributed.dht.GridDhtTxQueryEnlistRequest;
import org.apache.ignite.internal.processors.cache.distributed.dht.GridDhtTxQueryEnlistResponse;
import org.apache.ignite.internal.processors.cache.distributed.dht.GridDhtTxQueryFirstEnlistRequest;
import org.apache.ignite.internal.processors.cache.distributed.dht.GridDhtUnlockRequest;
import org.apache.ignite.internal.processors.cache.distributed.dht.GridInvokeValue;
import org.apache.ignite.internal.processors.cache.distributed.dht.PartitionUpdateCountersMessage;
import org.apache.ignite.internal.processors.cache.distributed.dht.atomic.GridDhtAtomicDeferredUpdateResponse;
import org.apache.ignite.internal.processors.cache.distributed.dht.atomic.GridDhtAtomicNearResponse;
import org.apache.ignite.internal.processors.cache.distributed.dht.atomic.GridDhtAtomicSingleUpdateRequest;
import org.apache.ignite.internal.processors.cache.distributed.dht.atomic.GridDhtAtomicUpdateRequest;
import org.apache.ignite.internal.processors.cache.distributed.dht.atomic.GridDhtAtomicUpdateResponse;
import org.apache.ignite.internal.processors.cache.distributed.dht.atomic.GridNearAtomicCheckUpdateRequest;
import org.apache.ignite.internal.processors.cache.distributed.dht.atomic.GridNearAtomicFullUpdateRequest;
import org.apache.ignite.internal.processors.cache.distributed.dht.atomic.GridNearAtomicSingleUpdateFilterRequest;
import org.apache.ignite.internal.processors.cache.distributed.dht.atomic.GridNearAtomicSingleUpdateInvokeRequest;
import org.apache.ignite.internal.processors.cache.distributed.dht.atomic.GridNearAtomicSingleUpdateRequest;
import org.apache.ignite.internal.processors.cache.distributed.dht.atomic.GridNearAtomicUpdateResponse;
import org.apache.ignite.internal.processors.cache.distributed.dht.atomic.NearCacheUpdates;
import org.apache.ignite.internal.processors.cache.distributed.dht.atomic.UpdateErrors;
import org.apache.ignite.internal.processors.cache.distributed.dht.preloader.CacheGroupAffinityMessage;
import org.apache.ignite.internal.processors.cache.distributed.dht.preloader.GridDhtForceKeysRequest;
import org.apache.ignite.internal.processors.cache.distributed.dht.preloader.GridDhtForceKeysResponse;
import org.apache.ignite.internal.processors.cache.distributed.dht.preloader.GridDhtPartitionDemandLegacyMessage;
import org.apache.ignite.internal.processors.cache.distributed.dht.preloader.GridDhtPartitionDemandMessage;
import org.apache.ignite.internal.processors.cache.distributed.dht.preloader.GridDhtPartitionExchangeId;
import org.apache.ignite.internal.processors.cache.distributed.dht.preloader.GridDhtPartitionSupplyMessage;
import org.apache.ignite.internal.processors.cache.distributed.dht.preloader.GridDhtPartitionSupplyMessageV2;
import org.apache.ignite.internal.processors.cache.distributed.dht.preloader.GridDhtPartitionsFullMessage;
import org.apache.ignite.internal.processors.cache.distributed.dht.preloader.GridDhtPartitionsSingleMessage;
import org.apache.ignite.internal.processors.cache.distributed.dht.preloader.GridDhtPartitionsSingleRequest;
import org.apache.ignite.internal.processors.cache.distributed.dht.preloader.latch.LatchAckMessage;
import org.apache.ignite.internal.processors.cache.distributed.near.CacheVersionedValue;
import org.apache.ignite.internal.processors.cache.distributed.near.GridNearGetRequest;
import org.apache.ignite.internal.processors.cache.distributed.near.GridNearGetResponse;
import org.apache.ignite.internal.processors.cache.distributed.near.GridNearLockRequest;
import org.apache.ignite.internal.processors.cache.distributed.near.GridNearLockResponse;
import org.apache.ignite.internal.processors.cache.distributed.near.GridNearSingleGetRequest;
import org.apache.ignite.internal.processors.cache.distributed.near.GridNearSingleGetResponse;
import org.apache.ignite.internal.processors.cache.distributed.near.GridNearTxEnlistRequest;
import org.apache.ignite.internal.processors.cache.distributed.near.GridNearTxEnlistResponse;
import org.apache.ignite.internal.processors.cache.distributed.near.GridNearTxFinishRequest;
import org.apache.ignite.internal.processors.cache.distributed.near.GridNearTxFinishResponse;
import org.apache.ignite.internal.processors.cache.distributed.near.GridNearTxPrepareRequest;
import org.apache.ignite.internal.processors.cache.distributed.near.GridNearTxPrepareResponse;
import org.apache.ignite.internal.processors.cache.distributed.near.GridNearTxQueryEnlistRequest;
import org.apache.ignite.internal.processors.cache.distributed.near.GridNearTxQueryEnlistResponse;
import org.apache.ignite.internal.processors.cache.distributed.near.GridNearTxQueryResultsEnlistRequest;
import org.apache.ignite.internal.processors.cache.distributed.near.GridNearTxQueryResultsEnlistResponse;
import org.apache.ignite.internal.processors.cache.distributed.near.GridNearUnlockRequest;
import org.apache.ignite.internal.processors.cache.mvcc.DeadlockProbe;
import org.apache.ignite.internal.processors.cache.mvcc.MvccSnapshotWithoutTxs;
import org.apache.ignite.internal.processors.cache.mvcc.MvccVersionImpl;
import org.apache.ignite.internal.processors.cache.mvcc.ProbedTx;
import org.apache.ignite.internal.processors.cache.mvcc.msg.MvccAckRequestQueryCntr;
import org.apache.ignite.internal.processors.cache.mvcc.msg.MvccAckRequestQueryId;
import org.apache.ignite.internal.processors.cache.mvcc.msg.MvccAckRequestTx;
import org.apache.ignite.internal.processors.cache.mvcc.msg.MvccAckRequestTxAndQueryCntr;
import org.apache.ignite.internal.processors.cache.mvcc.msg.MvccAckRequestTxAndQueryId;
import org.apache.ignite.internal.processors.cache.mvcc.msg.MvccActiveQueriesMessage;
import org.apache.ignite.internal.processors.cache.mvcc.msg.MvccFutureResponse;
import org.apache.ignite.internal.processors.cache.mvcc.msg.MvccQuerySnapshotRequest;
import org.apache.ignite.internal.processors.cache.mvcc.msg.MvccRecoveryFinishedMessage;
import org.apache.ignite.internal.processors.cache.mvcc.msg.MvccSnapshotResponse;
import org.apache.ignite.internal.processors.cache.mvcc.msg.MvccTxSnapshotRequest;
import org.apache.ignite.internal.processors.cache.mvcc.msg.PartitionCountersNeighborcastRequest;
import org.apache.ignite.internal.processors.cache.mvcc.msg.PartitionCountersNeighborcastResponse;
import org.apache.ignite.internal.processors.cache.query.GridCacheQueryRequest;
import org.apache.ignite.internal.processors.cache.query.GridCacheQueryResponse;
import org.apache.ignite.internal.processors.cache.query.GridCacheSqlQuery;
import org.apache.ignite.internal.processors.cache.query.continuous.CacheContinuousQueryBatchAck;
import org.apache.ignite.internal.processors.cache.query.continuous.CacheContinuousQueryEntry;
import org.apache.ignite.internal.processors.cache.transactions.IgniteTxEntry;
import org.apache.ignite.internal.processors.cache.transactions.IgniteTxKey;
import org.apache.ignite.internal.processors.cache.transactions.TxEntryValueHolder;
import org.apache.ignite.internal.processors.cache.transactions.TxLock;
import org.apache.ignite.internal.processors.cache.transactions.TxLockList;
import org.apache.ignite.internal.processors.cache.transactions.TxLocksRequest;
import org.apache.ignite.internal.processors.cache.transactions.TxLocksResponse;
import org.apache.ignite.internal.processors.cache.version.GridCacheRawVersionedEntry;
import org.apache.ignite.internal.processors.cache.version.GridCacheVersion;
import org.apache.ignite.internal.processors.cache.version.GridCacheVersionEx;
import org.apache.ignite.internal.processors.cluster.ClusterMetricsUpdateMessage;
import org.apache.ignite.internal.processors.continuous.ContinuousRoutineStartResultMessage;
import org.apache.ignite.internal.processors.continuous.GridContinuousMessage;
import org.apache.ignite.internal.processors.datastreamer.DataStreamerEntry;
import org.apache.ignite.internal.processors.datastreamer.DataStreamerRequest;
import org.apache.ignite.internal.processors.datastreamer.DataStreamerResponse;
import org.apache.ignite.internal.processors.hadoop.HadoopJobId;
import org.apache.ignite.internal.processors.hadoop.shuffle.HadoopDirectShuffleMessage;
import org.apache.ignite.internal.processors.hadoop.shuffle.HadoopShuffleAck;
import org.apache.ignite.internal.processors.hadoop.shuffle.HadoopShuffleFinishRequest;
import org.apache.ignite.internal.processors.hadoop.shuffle.HadoopShuffleFinishResponse;
import org.apache.ignite.internal.processors.hadoop.shuffle.HadoopShuffleMessage;
import org.apache.ignite.internal.processors.igfs.IgfsAckMessage;
import org.apache.ignite.internal.processors.igfs.IgfsBlockKey;
import org.apache.ignite.internal.processors.igfs.IgfsBlocksMessage;
import org.apache.ignite.internal.processors.igfs.IgfsDeleteMessage;
import org.apache.ignite.internal.processors.igfs.IgfsFileAffinityRange;
import org.apache.ignite.internal.processors.igfs.IgfsFragmentizerRequest;
import org.apache.ignite.internal.processors.igfs.IgfsFragmentizerResponse;
import org.apache.ignite.internal.processors.igfs.IgfsSyncMessage;
import org.apache.ignite.internal.processors.marshaller.MissingMappingRequestMessage;
import org.apache.ignite.internal.processors.marshaller.MissingMappingResponseMessage;
import org.apache.ignite.internal.processors.query.h2.twostep.messages.GridQueryCancelRequest;
import org.apache.ignite.internal.processors.query.h2.twostep.messages.GridQueryFailResponse;
import org.apache.ignite.internal.processors.query.h2.twostep.messages.GridQueryNextPageRequest;
import org.apache.ignite.internal.processors.query.h2.twostep.messages.GridQueryNextPageResponse;
import org.apache.ignite.internal.processors.query.messages.GridQueryKillRequest;
import org.apache.ignite.internal.processors.query.messages.GridQueryKillResponse;
import org.apache.ignite.internal.processors.query.schema.message.SchemaOperationStatusMessage;
import org.apache.ignite.internal.processors.rest.handlers.task.GridTaskResultRequest;
import org.apache.ignite.internal.processors.rest.handlers.task.GridTaskResultResponse;
import org.apache.ignite.internal.processors.service.ServiceDeploymentProcessId;
import org.apache.ignite.internal.processors.service.ServiceSingleNodeDeploymentResult;
import org.apache.ignite.internal.processors.service.ServiceSingleNodeDeploymentResultBatch;
import org.apache.ignite.internal.util.GridByteArrayList;
import org.apache.ignite.internal.util.GridIntList;
import org.apache.ignite.internal.util.GridLongList;
import org.apache.ignite.internal.util.GridMessageCollection;
import org.apache.ignite.internal.util.UUIDCollectionMessage;
import org.apache.ignite.lang.IgniteOutClosure;
import org.apache.ignite.plugin.extensions.communication.Message;
import org.apache.ignite.plugin.extensions.communication.MessageFactory;
import org.apache.ignite.spi.collision.jobstealing.JobStealingRequest;
import org.apache.ignite.spi.communication.tcp.TcpCommunicationSpi;
import org.apache.ignite.spi.communication.tcp.messages.HandshakeMessage;
import org.apache.ignite.spi.communication.tcp.messages.HandshakeMessage2;
import org.apache.ignite.spi.communication.tcp.messages.HandshakeWaitMessage;
import org.apache.ignite.spi.communication.tcp.messages.NodeIdMessage;
import org.apache.ignite.spi.communication.tcp.messages.RecoveryLastReceivedMessage;

/**
 * Message factory implementation.
 */
public class GridIoMessageFactory implements MessageFactory {
    /** Custom messages registry. Used for test purposes. */
    private static final Map<Short, IgniteOutClosure<Message>> CUSTOM = new ConcurrentHashMap<>();

    /** Extensions. */
    private final MessageFactory[] ext;

    /**
     * @param ext Extensions.
     */
    public GridIoMessageFactory(MessageFactory[] ext) {
        this.ext = ext;
    }

    /** {@inheritDoc} */
    @Override public Message create(short type) {
        Message msg = null;

        switch (type) {
            // -54 is reserved for SQL.
            // -46 ... -51 - snapshot messages.
            case -61:
                msg = new IgniteDiagnosticMessage();

                break;

            case -53:
                msg = new SchemaOperationStatusMessage();

                break;

            case -52:
                msg = new GridIntList();

                break;

            case -51:
                msg = new NearCacheUpdates();

                break;

            case -50:
                msg = new GridNearAtomicCheckUpdateRequest();

                break;

            case -49:
                msg = new UpdateErrors();

                break;

            case -48:
                msg = new GridDhtAtomicNearResponse();

                break;

            case -45:
                msg = new GridChangeGlobalStateMessageResponse();

                break;

            case -44:
                msg = new HandshakeMessage2();

                break;

            case -43:
                msg = new IgniteIoTestMessage();

                break;

            case -42:
                msg = new HadoopDirectShuffleMessage();

                break;

            case -41:
                msg = new HadoopShuffleFinishResponse();

                break;

            case -40:
                msg = new HadoopShuffleFinishRequest();

                break;

            case -39:
                msg = new HadoopJobId();

                break;

            case -38:
                msg = new HadoopShuffleAck();

                break;

            case -37:
                msg = new HadoopShuffleMessage();

                break;

            case -36:
                msg = new GridDhtAtomicSingleUpdateRequest();

                break;

            case -27:
                msg = new GridDhtTxOnePhaseCommitAckRequest();

                break;

            case -26:
                msg = new TxLockList();

                break;

            case -25:
                msg = new TxLock();

                break;

            case -24:
                msg = new TxLocksRequest();

                break;

            case -23:
                msg = new TxLocksResponse();

                break;

            case TcpCommunicationSpi.NODE_ID_MSG_TYPE:
                msg = new NodeIdMessage();

                break;

            case TcpCommunicationSpi.RECOVERY_LAST_ID_MSG_TYPE:
                msg = new RecoveryLastReceivedMessage();

                break;

            case TcpCommunicationSpi.HANDSHAKE_MSG_TYPE:
                msg = new HandshakeMessage();

                break;

            case TcpCommunicationSpi.HANDSHAKE_WAIT_MSG_TYPE:
                msg = new HandshakeWaitMessage();

                break;

            case 0:
                msg = new GridJobCancelRequest();

                break;

            case 1:
                msg = new GridJobExecuteRequest();

                break;

            case 2:
                msg = new GridJobExecuteResponse();

                break;

            case 3:
                msg = new GridJobSiblingsRequest();

                break;

            case 4:
                msg = new GridJobSiblingsResponse();

                break;

            case 5:
                msg = new GridTaskCancelRequest();

                break;

            case 6:
                msg = new GridTaskSessionRequest();

                break;

            case 7:
                msg = new GridCheckpointRequest();

                break;

            case 8:
                msg = new GridIoMessage();

                break;

            case 9:
                msg = new GridIoUserMessage();

                break;

            case 10:
                msg = new GridDeploymentInfoBean();

                break;

            case 11:
                msg = new GridDeploymentRequest();

                break;

            case 12:
                msg = new GridDeploymentResponse();

                break;

            case 13:
                msg = new GridEventStorageMessage();

                break;

            case 16:
                msg = new GridCacheTxRecoveryRequest();

                break;

            case 17:
                msg = new GridCacheTxRecoveryResponse();

                break;

            case 20:
                msg = new GridCacheTtlUpdateRequest();

                break;

            case 21:
                msg = new GridDistributedLockRequest();

                break;

            case 22:
                msg = new GridDistributedLockResponse();

                break;

            case 23:
                msg = new GridDistributedTxFinishRequest();

                break;

            case 24:
                msg = new GridDistributedTxFinishResponse();

                break;

            case 25:
                msg = new GridDistributedTxPrepareRequest();

                break;

            case 26:
                msg = new GridDistributedTxPrepareResponse();

                break;

            case 27:
                msg = new GridDistributedUnlockRequest();

                break;

            case 28:
                msg = new GridDhtAffinityAssignmentRequest();

                break;

            case 29:
                msg = new GridDhtAffinityAssignmentResponse();

                break;

            case 30:
                msg = new GridDhtLockRequest();

                break;

            case 31:
                msg = new GridDhtLockResponse();

                break;

            case 32:
                msg = new GridDhtTxFinishRequest();

                break;

            case 33:
                msg = new GridDhtTxFinishResponse();

                break;

            case 34:
                msg = new GridDhtTxPrepareRequest();

                break;

            case 35:
                msg = new GridDhtTxPrepareResponse();

                break;

            case 36:
                msg = new GridDhtUnlockRequest();

                break;

            case 37:
                msg = new GridDhtAtomicDeferredUpdateResponse();

                break;

            case 38:
                msg = new GridDhtAtomicUpdateRequest();

                break;

            case 39:
                msg = new GridDhtAtomicUpdateResponse();

                break;

            case 40:
                msg = new GridNearAtomicFullUpdateRequest();

                break;

            case 41:
                msg = new GridNearAtomicUpdateResponse();

                break;

            case 42:
                msg = new GridDhtForceKeysRequest();

                break;

            case 43:
                msg = new GridDhtForceKeysResponse();

                break;

            case 44:
                msg = new GridDhtPartitionDemandLegacyMessage();

                break;

            case 45:
                msg = new GridDhtPartitionDemandMessage();

                break;

            case 46:
                msg = new GridDhtPartitionsFullMessage();

                break;

            case 47:
                msg = new GridDhtPartitionsSingleMessage();

                break;

            case 48:
                msg = new GridDhtPartitionsSingleRequest();

                break;

            case 49:
                msg = new GridNearGetRequest();

                break;

            case 50:
                msg = new GridNearGetResponse();

                break;

            case 51:
                msg = new GridNearLockRequest();

                break;

            case 52:
                msg = new GridNearLockResponse();

                break;

            case 53:
                msg = new GridNearTxFinishRequest();

                break;

            case 54:
                msg = new GridNearTxFinishResponse();

                break;

            case 55:
                msg = new GridNearTxPrepareRequest();

                break;

            case 56:
                msg = new GridNearTxPrepareResponse();

                break;

            case 57:
                msg = new GridNearUnlockRequest();

                break;

            case 58:
                msg = new GridCacheQueryRequest();

                break;

            case 59:
                msg = new GridCacheQueryResponse();

                break;

            case 61:
                msg = new GridContinuousMessage();

                break;

            case 62:
                msg = new DataStreamerRequest();

                break;

            case 63:
                msg = new DataStreamerResponse();

                break;

            case 64:
                msg = new IgfsAckMessage();

                break;

            case 65:
                msg = new IgfsBlockKey();

                break;

            case 66:
                msg = new IgfsBlocksMessage();

                break;

            case 67:
                msg = new IgfsDeleteMessage();

                break;

            case 68:
                msg = new IgfsFileAffinityRange();

                break;

            case 69:
                msg = new IgfsFragmentizerRequest();

                break;

            case 70:
                msg = new IgfsFragmentizerResponse();

                break;

            case 71:
                msg = new IgfsSyncMessage();

                break;

            case 76:
                msg = new GridTaskResultRequest();

                break;

            case 77:
                msg = new GridTaskResultResponse();

                break;

            case 78:
                msg = new MissingMappingRequestMessage();

                break;

            case 79:
                msg = new MissingMappingResponseMessage();

                break;

            case 80:
                msg = new MetadataRequestMessage();

                break;

            case 81:
                msg = new MetadataResponseMessage();

                break;

            case 82:
                msg = new JobStealingRequest();

                break;

            case 84:
                msg = new GridByteArrayList();

                break;

            case 85:
                msg = new GridLongList();

                break;

            case 86:
                msg = new GridCacheVersion();

                break;

            case 87:
                msg = new GridDhtPartitionExchangeId();

                break;

            case 88:
                msg = new GridCacheReturn();

                break;

            case 89:
                msg = new CacheObjectImpl();

                break;

            case 90:
                msg = new KeyCacheObjectImpl();

                break;

            case 91:
                msg = new GridCacheEntryInfo();

                break;

            case 92:
                msg = new CacheEntryInfoCollection();

                break;

            case 93:
                msg = new CacheInvokeDirectResult();

                break;

            case 94:
                msg = new IgniteTxKey();

                break;

            case 95:
                msg = new DataStreamerEntry();

                break;

            case 96:
                msg = new CacheContinuousQueryEntry();

                break;

            case 97:
                msg = new CacheEvictionEntry();

                break;

            case 98:
                msg = new CacheEntryPredicateContainsValue();

                break;

            case 99:
                msg = new CacheEntrySerializablePredicate();

                break;

            case 100:
                msg = new IgniteTxEntry();

                break;

            case 101:
                msg = new TxEntryValueHolder();

                break;

            case 102:
                msg = new CacheVersionedValue();

                break;

            case 103:
                msg = new GridCacheRawVersionedEntry<>();

                break;

            case 104:
                msg = new GridCacheVersionEx();

                break;

            case 105:
                msg = new CacheObjectByteArrayImpl();

                break;

            case 106:
                msg = new GridQueryCancelRequest();

                break;

            case 107:
                msg = new GridQueryFailResponse();

                break;

            case 108:
                msg = new GridQueryNextPageRequest();

                break;

            case 109:
                msg = new GridQueryNextPageResponse();

                break;

            case 110:
                // EMPTY type
                // GridQueryRequest was removed
                break;

            case 111:
                msg = new AffinityTopologyVersion();

                break;

            case 112:
                msg = new GridCacheSqlQuery();

                break;

            case 113:
                msg = new BinaryObjectImpl();

                break;

            case 114:
                msg = new GridDhtPartitionSupplyMessage();

                break;

            case 115:
                msg = new UUIDCollectionMessage();

                break;

            case 116:
                msg = new GridNearSingleGetRequest();

                break;

            case 117:
                msg = new GridNearSingleGetResponse();

                break;

            case 118:
                msg = new CacheContinuousQueryBatchAck();

                break;

            case 119:
                msg = new BinaryEnumObjectImpl();

                break;

            // [120..123] - DR
            case 124:
                msg = new GridMessageCollection<>();

                break;

            case 125:
                msg = new GridNearAtomicSingleUpdateRequest();

                break;

            case 126:
                msg = new GridNearAtomicSingleUpdateInvokeRequest();

                break;

            case 127:
                msg = new GridNearAtomicSingleUpdateFilterRequest();

                break;

            case 128:
                msg = new CacheGroupAffinityMessage();

                break;

            case 129:
                msg = new WalStateAckMessage();

                break;

            case 130:
                msg = new UserManagementOperationFinishedMessage();

                break;

            case 131:
                msg = new UserAuthenticateRequestMessage();

                break;

            case 132:
                msg = new UserAuthenticateResponseMessage();

                break;

            case 133:
                msg = new ClusterMetricsUpdateMessage();

                break;

            case 134:
                msg = new ContinuousRoutineStartResultMessage();

                break;

            case 135:
                msg = new LatchAckMessage();

                break;

            case 136:
                msg = new MvccTxSnapshotRequest();

                break;

            case 137:
                msg = new MvccAckRequestTx();

                break;

            case 138:
                msg = new MvccFutureResponse();

                break;

            case 139:
                msg = new MvccQuerySnapshotRequest();

                break;

            case 140:
                msg = new MvccAckRequestQueryCntr();

                break;

            case 141:
                msg = new MvccSnapshotResponse();

                break;

            case 143:
                msg = new GridCacheMvccEntryInfo();

                break;

            case 144:
                msg = new GridDhtTxQueryEnlistResponse();

                break;

            case 145:
                msg = new MvccAckRequestQueryId();

                break;

            case 146:
                msg = new MvccAckRequestTxAndQueryCntr();

                break;

            case 147:
                msg = new MvccAckRequestTxAndQueryId();

                break;

            case 148:
                msg = new MvccVersionImpl();

                break;

            case 149:
                msg = new MvccActiveQueriesMessage();

                break;

            case 150:
                msg = new MvccSnapshotWithoutTxs();

                break;

            case 151:
                msg = new GridNearTxQueryEnlistRequest();

                break;

            case 152:
                msg = new GridNearTxQueryEnlistResponse();

                break;

            case 153:
                msg = new GridNearTxQueryResultsEnlistRequest();

                break;

            case 154:
                msg = new GridNearTxQueryResultsEnlistResponse();

                break;

            case 155:
                msg = new GridDhtTxQueryEnlistRequest();

                break;

            case 156:
                msg = new GridDhtTxQueryFirstEnlistRequest();

                break;

            case 157:
                msg = new PartitionUpdateCountersMessage();

                break;

            case 158:
                msg = new GridDhtPartitionSupplyMessageV2();

                break;

            case 159:
                msg = new GridNearTxEnlistRequest();

                break;

            case 160:
                msg = new GridNearTxEnlistResponse();

                break;

            case 161:
                msg = new GridInvokeValue();

                break;

            case 162:
                msg = new GenerateEncryptionKeyRequest();

                break;

            case 163:
                msg = new GenerateEncryptionKeyResponse();

                break;

            case 164:
                msg = new MvccRecoveryFinishedMessage();

                break;

            case 165:
                msg = new PartitionCountersNeighborcastRequest();

                break;

            case 166:
                msg = new PartitionCountersNeighborcastResponse();

                break;

            case 167:
                msg = new ServiceDeploymentProcessId();

                break;

            case 168:
                msg = new ServiceSingleNodeDeploymentResultBatch();

                break;

            case 169:
                msg = new ServiceSingleNodeDeploymentResult();

                break;

            case 170:
                msg = new DeadlockProbe();

                break;

            case 171:
                msg = new ProbedTx();

                break;

            case GridQueryKillRequest.TYPE_CODE:
                msg = new GridQueryKillRequest();

                break;

            case GridQueryKillResponse.TYPE_CODE:
                msg = new GridQueryKillResponse();

                break;

            // [-3..119] [124..129] [-23..-28] [-36..-55] - this
            // [120..123] - DR
            // [-4..-22, -30..-35] - SQL
            // [2048..2053] - Snapshots
            // [4096..4096] - TxDR
            default:
                if (ext != null) {
                    for (MessageFactory factory : ext) {
                        msg = factory.create(type);

                        if (msg != null)
                            break;
                    }
                }

                if (msg == null) {
                    IgniteOutClosure<Message> c = CUSTOM.get(type);

                    if (c != null)
                        msg = c.apply();
                }
        }

        if (msg == null)
            throw new IgniteException("Invalid message type: " + type);

        return msg;
    }

    /**
     * Registers factory for custom message. Used for test purposes.
     *
     * @param type Message type.
     * @param c Message producer.
     */
    public static void registerCustom(short type, IgniteOutClosure<Message> c) {
        assert c != null;

        CUSTOM.put(type, c);
    }
}
