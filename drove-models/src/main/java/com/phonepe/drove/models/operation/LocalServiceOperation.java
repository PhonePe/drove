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

package com.phonepe.drove.models.operation;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.phonepe.drove.models.operation.localserviceops.*;
import lombok.Data;

/**
 *
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(name = "START", value = LocalServiceCreateOperation.class),
        @JsonSubTypes.Type(name = "ACTIVATE", value = LocalServiceActivateOperation.class),
        @JsonSubTypes.Type(name = "SCALE", value = LocalServiceScaleOperation.class),
        @JsonSubTypes.Type(name = "STOP", value = LocalServiceDeactivateOperation.class),
        @JsonSubTypes.Type(name = "RESTART", value = LocalServiceRestartOperation.class),
        @JsonSubTypes.Type(name = "UPDATE", value = LocalServiceUpdateOperation.class),
        @JsonSubTypes.Type(name = "DESTROY", value = LocalServiceDestroyOperation.class),
})
@Data
public abstract class LocalServiceOperation {
    private final LocalServiceOperationType type;

    public abstract <T> T accept(final LocalServiceOperationVisitor<T> visitor);
}
