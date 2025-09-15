package com.phonepe.drove.executor.discovery;

import com.google.common.annotations.VisibleForTesting;
import com.phonepe.drove.common.discovery.NodeDataStore;
import com.phonepe.drove.common.model.MessageDeliveryStatus;
import com.phonepe.drove.common.model.MessageHeader;
import com.phonepe.drove.common.model.controller.ExecutorSnapshotMessage;
import com.phonepe.drove.executor.ExecutorOptions;
import com.phonepe.drove.executor.engine.ExecutorCommunicator;
import com.phonepe.drove.models.info.nodedata.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import java.util.List;
import java.util.Objects;

/**
 * This node data store is to be used by executors to send data to controller only. It is not supposed to
 * store any data locally. Depending on config, it might route to either ZK or HTTP calls.
 */
@Slf4j
@AllArgsConstructor(access = AccessLevel.PROTECTED, onConstructor_ = {@VisibleForTesting})
public class RemoteNodeDataStore implements NodeDataStore {
    private final RemoteUpdateMode remoteUpdateMode;
    private final Provider<ExecutorCommunicator> communicator;
    private final NodeDataStore remoteStore;

    @Inject
    public RemoteNodeDataStore(
            ExecutorOptions executorOptions,
            Provider<ExecutorCommunicator> communicator,
            @Named("PersistentNodeDataStore") NodeDataStore remoteStore) {
        this(Objects.requireNonNullElse(executorOptions.getRemoteUpdateMode(), RemoteUpdateMode.RPC),
             communicator,
             remoteStore);
    }

    @Override
    public void updateNodeData(NodeData nodeData) {
        val status = nodeData.accept(new NodeDataVisitor<Boolean>() {
            @Override
            public Boolean visit(ControllerNodeData controllerData) {
                return null;
            }

            @Override
            public Boolean visit(ExecutorNodeData executorData) {
                try {
                    switch (remoteUpdateMode) {
                        case STORE -> {
                            remoteStore.updateNodeData(executorData);
                        }
                        case RPC -> {
                            val response =
                                    communicator.get()
                                            .send(new ExecutorSnapshotMessage(MessageHeader.controllerRequest(),
                                                                              executorData));
                            if (!response.getStatus().equals(MessageDeliveryStatus.ACCEPTED)) {
                                log.warn("RPC based update failed. Reverting to store based state update.");
                                remoteStore.updateNodeData(executorData);
                            }
                        }
                    }
                    return true;
                }
                catch (Throwable t) {
                    log.error("Could not update node state: {}", t.getMessage(), t);
                }
                return false;
            }
        });
        log.debug("Remote state update status: {}", status);
    }

    @Override
    public List<NodeData> nodes(NodeType nodeType) {
        return remoteStore.nodes(nodeType);
    }

    @Override
    public void removeNodeData(NodeData nodeData) {
        throw new UnsupportedOperationException("Not supported in remote mode");
    }
}
