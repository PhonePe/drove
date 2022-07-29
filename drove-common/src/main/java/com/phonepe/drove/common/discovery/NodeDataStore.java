package com.phonepe.drove.common.discovery;


import com.phonepe.drove.models.info.nodedata.NodeData;
import com.phonepe.drove.models.info.nodedata.NodeType;

import java.util.List;

/**
 *
 */
public interface NodeDataStore {

    void updateNodeData(final NodeData nodeData);

    List<NodeData> nodes(final NodeType nodeType);

    void removeNodeData(final NodeData nodeData);
}
