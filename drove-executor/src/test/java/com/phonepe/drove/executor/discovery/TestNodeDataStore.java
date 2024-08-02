/*
 *  Copyright (c) 2022 Original Author(s), PhonePe India Pvt. Ltd.
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
import com.phonepe.drove.models.info.nodedata.NodeData;
import com.phonepe.drove.models.info.nodedata.NodeType;
import io.appform.signals.signals.ConsumingSyncSignal;
import lombok.val;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 *
 */
public class TestNodeDataStore implements NodeDataStore {
    private final Map<String, NodeData> nodes = new ConcurrentHashMap<>();

    private final ConsumingSyncSignal<NodeData> updated = new ConsumingSyncSignal<>();

    @Override
    public void updateNodeData(NodeData nodeData) {
        val nodeId = id(nodeData);
        nodes.compute(nodeId, (id, v) -> nodeData);
        updated.dispatch(nodeData);
    }

    @Override
    public List<NodeData> nodes(NodeType nodeType) {
        return nodes.values()
                .stream()
                .filter(node -> node.getType().equals(nodeType))
                .toList();
    }

    @Override
    public void removeNodeData(NodeData nodeData) {
        nodes.remove(id(nodeData));
    }

    public ConsumingSyncSignal<NodeData> onNodeDataUpdate() {
        return updated;
    }

    private String id(final NodeData node) {
        return UUID.nameUUIDFromBytes(
                String.format("%s%s%d", node.getType().name(), node.getHostname(), node.getPort())
                        .getBytes(StandardCharsets.UTF_8)).toString();
    }
}
