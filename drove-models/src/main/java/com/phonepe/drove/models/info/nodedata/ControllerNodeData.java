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

package com.phonepe.drove.models.info.nodedata;

import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;

import java.util.Date;

/**
 *
 */
@Value
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class ControllerNodeData extends NodeData {
    boolean leader;

    public ControllerNodeData(
            String hostname,
            int port,
            NodeTransportType transportType,
            Date updated,
            boolean leader) {
        super(NodeType.CONTROLLER, hostname, port, transportType, updated);
        this.leader = leader;
    }


    @Override
    public <T> T accept(NodeDataVisitor<T> visitor) {
        return visitor.visit(this);
    }

    public static ControllerNodeData from(final ControllerNodeData nodeData, boolean leader) {
        return new ControllerNodeData(nodeData.getHostname(),
                                      nodeData.getPort(),
                                      nodeData.getTransportType(),
                                      new Date(),
                                      leader);
    }
}
