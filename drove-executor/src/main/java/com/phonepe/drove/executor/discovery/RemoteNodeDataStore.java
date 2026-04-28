/*
 *  Copyright (c) 2025 Original Author(s), PhonePe India Pvt. Ltd.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.phonepe.drove.executor.discovery;

import com.phonepe.drove.common.discovery.NodeDataStore;
import com.phonepe.drove.common.model.MessageDeliveryStatus;
import com.phonepe.drove.common.model.MessageHeader;
import com.phonepe.drove.common.model.controller.ExecutorSnapshotMessage;
import com.phonepe.drove.executor.engine.ExecutorCommunicator;
import com.phonepe.drove.models.info.nodedata.*;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;

/**
 * This node data store is to be used by executors to send data to controller only. It is not supposed to
 * store any data locally. Depending on config, it might route to either ZK or HTTP calls. In case HTTP call
 * fails, it will route the call to ZK.
 */
@Slf4j
@Singleton
@AllArgsConstructor(onConstructor_ = {@Inject})
public class RemoteNodeDataStore implements NodeDataStore {
    private final ExecutorCommunicator communicator;

    @Override
    @SuppressWarnings("java:S1301")
    public void updateNodeData(NodeData nodeData) {
        val status = nodeData.accept(new NodeDataVisitor<Boolean>() {
            @Override
            public Boolean visit(ControllerNodeData controllerData) {
                throw new IllegalArgumentException("Invalid data. Why is executor sending controller data here?");
            }

            @Override
            public Boolean visit(ExecutorNodeData executorData) {
                try {
                    val remoteMessageSent = sendRemoteMessage(executorData);
                    if (!remoteMessageSent) {
                        log.error("RPC based update failed. Reverting to store based state update.");
                    }
                    return remoteMessageSent;
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
        throw new UnsupportedOperationException("Remote store being called for nodes(). This is not supported.");
    }

    @Override
    public void removeNodeData(NodeData nodeData) {
        throw new UnsupportedOperationException("Not supported in remote mode");
    }

    private boolean sendRemoteMessage(ExecutorNodeData executorNodeData) {
        try {
            val response = communicator
                            .send(new ExecutorSnapshotMessage(MessageHeader.controllerRequest(), executorNodeData));
            return response.getStatus().equals(MessageDeliveryStatus.ACCEPTED);
        }
        catch (Exception e) {
            log.error("RPC based update failed due to error: ", e);
        }
        return false;
    }
}
