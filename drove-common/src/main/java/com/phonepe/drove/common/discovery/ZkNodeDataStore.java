/*
 *  Copyright (c) 2024 Original Author(s), PhonePe India Pvt. Ltd.
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

package com.phonepe.drove.common.discovery;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.phonepe.drove.models.info.nodedata.NodeData;
import com.phonepe.drove.models.info.nodedata.NodeType;
import io.appform.functionmetrics.MonitoredFunction;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.curator.framework.CuratorFramework;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 *
 */
@Slf4j
@Singleton
@SuppressWarnings("java:S1075")
public class ZkNodeDataStore implements NodeDataStore {

    final CuratorFramework curator;
    final ObjectMapper mapper;

    @Inject
    public ZkNodeDataStore(CuratorFramework curator, ObjectMapper mapper) {
        this.curator = curator;
        this.mapper = mapper;
    }

    @Override
    @MonitoredFunction
    public void updateNodeData(NodeData nodeData) {
        try {
            curator.create()
                    .orSetData()
                    .creatingParentContainersIfNeeded()
                    .withMode(CreateMode.EPHEMERAL)
                    .forPath(nodePath(nodeData), mapper.writeValueAsBytes(nodeData));
            log.debug("Node data updated in store");
        }
        catch (Exception e) {
            log.error("Could not update node data", e);
        }
    }

    @Override
    @MonitoredFunction
    public List<NodeData> nodes(NodeType nodeType) {
        try {
            val parentPath = "/" + nodeType.name().toLowerCase();
            return curator.getChildren()
                    .forPath(parentPath)
                    .stream()
                    .map(childPath -> readChild(parentPath, childPath).orElse(null))
                    .filter(Objects::nonNull)
                    .map(nodeData -> {
                        try {
                            return mapper.readValue(nodeData, NodeData.class);
                        }
                        catch (IOException e) {
                            log.error("Could not read node data", e);
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .toList();
        }
        catch (KeeperException.NoNodeException e) {
            log.trace("No controller found");
        }
        catch (Exception e) {
            log.error("Could not get nodes: ", e);
        }
        return Collections.emptyList();
    }

    @Override
    @MonitoredFunction
    public void removeNodeData(NodeData nodeData) {
        try {
            curator.delete()
                    .forPath(nodePath(nodeData));
        } catch (Exception e) {
            log.error("Could not delete node: ", e);
        }
    }

    private Optional<byte[]> readChild(String parentPath, String child) {
        val path = String.format("%s/%s", parentPath, child);
        try {
            return Optional.ofNullable(curator.getData().forPath(path));
        }
        catch (KeeperException.NoNodeException e) {
            log.warn("Node not found for path {}", path);
            return Optional.empty();
        }
        catch (Exception e) {
            log.error("Could not get data for node: " + path, e);
            return Optional.empty();
        }
    }

    private String nodePath(final NodeData nodeData) {
        return String.format("/%s/%s-%d",
                             nodeData.getType().name().toLowerCase(),
                             nodeData.getHostname(),
                             nodeData.getPort());
    }
}
