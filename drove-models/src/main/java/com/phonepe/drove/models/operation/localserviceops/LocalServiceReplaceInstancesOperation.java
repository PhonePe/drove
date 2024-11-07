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

package com.phonepe.drove.models.operation.localserviceops;

import com.phonepe.drove.models.operation.ClusterOpSpec;
import com.phonepe.drove.models.operation.LocalServiceOperation;
import com.phonepe.drove.models.operation.LocalServiceOperationType;
import com.phonepe.drove.models.operation.LocalServiceOperationVisitor;
import lombok.*;
import lombok.extern.jackson.Jacksonized;

import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import java.util.Collections;
import java.util.Set;

/**
 *
 */
@Value
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Jacksonized
@Builder
@With
public class LocalServiceReplaceInstancesOperation extends LocalServiceOperation {
    @NotEmpty
    String serviceId;

    Set<String> instanceIds;

    boolean stopFirst;

    @Valid
    ClusterOpSpec opSpec;

    public LocalServiceReplaceInstancesOperation(
            String serviceId,
            Set<String> instanceIds,
            boolean stopFirst,
            ClusterOpSpec opSpec) {
        super(LocalServiceOperationType.REPLACE_INSTANCES);
        this.serviceId = serviceId;
        this.instanceIds = instanceIds == null ? Collections.emptySet() : instanceIds;
        this.stopFirst = stopFirst;
        this.opSpec = opSpec;
    }

    @Override
    public <T> T accept(LocalServiceOperationVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
