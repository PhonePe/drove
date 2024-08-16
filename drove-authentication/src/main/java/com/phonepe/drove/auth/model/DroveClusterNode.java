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

package com.phonepe.drove.auth.model;

import com.phonepe.drove.models.info.nodedata.NodeType;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;

/**
 * Represents an authenticated cluster controller node
 */
@Value
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class DroveClusterNode extends DroveUser {
    NodeType nodeType;

    public DroveClusterNode(String id, NodeType nodeType) {
        super(DroveUserType.CLUSTER_NODE, id, DroveUserRole.CLUSTER_NODE);
        this.nodeType = nodeType;
    }

    @Override
    public <T> T accept(DroveUserVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
