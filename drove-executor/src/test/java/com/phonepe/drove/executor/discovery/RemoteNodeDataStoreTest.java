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
import com.phonepe.drove.common.model.Message;
import com.phonepe.drove.common.model.MessageDeliveryStatus;
import com.phonepe.drove.common.model.MessageResponse;
import com.phonepe.drove.executor.ExecutorOptions;
import com.phonepe.drove.executor.engine.ExecutorCommunicator;
import com.phonepe.drove.models.info.nodedata.*;
import lombok.val;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests {@link RemoteNodeDataStore}
 */
class RemoteNodeDataStoreTest {

    @Test
    void testRpcSendSuccess() {
        val communicatorCalled = new AtomicBoolean(false);
        val upstreamCalled = new AtomicBoolean(false);
        val communicator = mock(ExecutorCommunicator.class);
        val upstream = mock(NodeDataStore.class);
        val nds = new RemoteNodeDataStore(ExecutorOptions.DEFAULT,
                                          () -> communicator,
                                          upstream);
        when(communicator.send(any()))
                .thenAnswer(invocationMock -> {
                    val message = invocationMock.getArgument(0, Message.class);
                    communicatorCalled.set(true);
                    return new MessageResponse(message.getHeader(), MessageDeliveryStatus.ACCEPTED);
                });
        doAnswer(invocationOnMock -> {
            upstreamCalled.set(true);
            return null;
        }).when(upstream).updateNodeData(any(NodeData.class));
        val data = generateDummyData();
        nds.updateNodeData(data);
        assertTrue(communicatorCalled.get());
        assertFalse(upstreamCalled.get());
    }

    @Test
    void testUpstreamSendSuccess() {
        val communicatorCalled = new AtomicBoolean(false);
        val upstreamCalled = new AtomicBoolean(false);
        val communicator = mock(ExecutorCommunicator.class);
        val upstream = mock(NodeDataStore.class);
        val nds = new RemoteNodeDataStore(ExecutorOptions.DEFAULT
                                                  .withRemoteUpdateMode(RemoteUpdateMode.STORE),
                                          () -> communicator,
                                          upstream);
        when(communicator.send(any()))
                .thenAnswer(invocationMock -> {
                    val message = invocationMock.getArgument(0, Message.class);
                    communicatorCalled.set(true);
                    return new MessageResponse(message.getHeader(), MessageDeliveryStatus.ACCEPTED);
                });
        doAnswer(invocationOnMock -> {
            upstreamCalled.set(true);
            return null;
        }).when(upstream).updateNodeData(any(NodeData.class));
        val data = generateDummyData();
        nds.updateNodeData(data);
        assertFalse(communicatorCalled.get());
        assertTrue(upstreamCalled.get());
    }

    @Test
    void testUpstreamFallbackOnFailure() {
        val communicatorCalled = new AtomicBoolean(false);
        val upstreamCalled = new AtomicBoolean(false);
        val communicator = mock(ExecutorCommunicator.class);
        val upstream = mock(NodeDataStore.class);
        val nds = new RemoteNodeDataStore(ExecutorOptions.DEFAULT.withRemoteUpdateMode(null), //Default to RPC
                                          () -> communicator,
                                          upstream);
        when(communicator.send(any()))
                .thenAnswer(invocationMock -> {
                    val message = invocationMock.getArgument(0, Message.class);
                    communicatorCalled.set(true);
                    return new MessageResponse(message.getHeader(), MessageDeliveryStatus.FAILED);
                });
        doAnswer(invocationOnMock -> {
            upstreamCalled.set(true);
            return null;
        }).when(upstream).updateNodeData(any(NodeData.class));
        val data = generateDummyData();
        nds.updateNodeData(data);
        assertTrue(communicatorCalled.get());
        assertTrue(upstreamCalled.get());
    }

    @Test
    void testUpstreamFallbackOnError() {
        val communicatorCalled = new AtomicBoolean(false);
        val upstreamCalled = new AtomicBoolean(false);
        val communicator = mock(ExecutorCommunicator.class);
        val upstream = mock(NodeDataStore.class);
        val nds = new RemoteNodeDataStore(RemoteUpdateMode.RPC,
                                          () -> communicator,
                                          upstream);
        when(communicator.send(any()))
                .thenAnswer(invocationMock -> {
                    communicatorCalled.set(true);
                    throw new IllegalStateException("Simulated comms error");
                });
        doAnswer(invocationOnMock -> {
            upstreamCalled.set(true);
            return null;
        }).when(upstream).updateNodeData(any(NodeData.class));
        val data = generateDummyData();
        nds.updateNodeData(data);
        assertTrue(communicatorCalled.get());
        assertTrue(upstreamCalled.get());
    }

    @Test
    void testFailure() {
        val communicatorCalled = new AtomicBoolean(false);
        val upstreamCalled = new AtomicBoolean(false);
        val communicator = mock(ExecutorCommunicator.class);
        val upstream = mock(NodeDataStore.class);
        val nds = new RemoteNodeDataStore(RemoteUpdateMode.RPC,
                                          () -> communicator,
                                          upstream);
        when(communicator.send(any()))
                .thenAnswer(invocationMock -> {
                    communicatorCalled.set(true);
                    throw new IllegalStateException("Simulated comms error");
                });
        doAnswer(invocationOnMock -> {
            upstreamCalled.set(true);
            throw new IllegalStateException("Update failure");
        }).when(upstream).updateNodeData(any(NodeData.class));
        val data = generateDummyData();
        nds.updateNodeData(data);
        assertTrue(communicatorCalled.get());
        assertTrue(upstreamCalled.get());
    }

    @Test
    void testIllegalOps() {
        val communicator = mock(ExecutorCommunicator.class);
        val upstream = mock(NodeDataStore.class);
        val nds = new RemoteNodeDataStore(RemoteUpdateMode.RPC,
                                          () -> communicator,
                                          upstream);
        assertThrows(IllegalArgumentException.class,
                     () -> nds.updateNodeData(new ControllerNodeData(null, 0, null, null, false)));
        assertThrows(UnsupportedOperationException.class,
                     () -> nds.removeNodeData(null));
    }

    @Test
    void testNodePassthrough() {
        val nodesCalled = new AtomicBoolean(false);
        val communicator = mock(ExecutorCommunicator.class);
        val upstream = mock(NodeDataStore.class);
        val nds = new RemoteNodeDataStore(RemoteUpdateMode.RPC,
                                          () -> communicator,
                                          upstream);
        when(upstream.nodes(any()))
                .thenAnswer(invocationOnMock -> {
                    nodesCalled.set(true);
                    return List.of();
                });
        assertTrue(nds.nodes(NodeType.CONTROLLER).isEmpty());
        assertTrue(nodesCalled.get());
    }

    private static ExecutorNodeData generateDummyData() {
        return new ExecutorNodeData("localhost",
                                    8080,
                                    NodeTransportType.HTTP,
                                    new Date(),
                                    null,
                                    List.of(),
                                    List.of(),
                                    List.of(),
                                    Set.of(),
                                    Map.of(),
                                    ExecutorState.ACTIVE);
    }
}