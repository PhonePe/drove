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
import com.phonepe.drove.models.operation.ops.*;
import io.swagger.v3.oas.annotations.media.DiscriminatorMapping;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * Base class for application operations
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(name = "CREATE", value = ApplicationCreateOperation.class),
        @JsonSubTypes.Type(name = "DESTROY", value = ApplicationDestroyOperation.class),
        @JsonSubTypes.Type(name = "START_INSTANCES", value = ApplicationStartInstancesOperation.class),
        @JsonSubTypes.Type(name = "STOP_INSTANCES", value = ApplicationStopInstancesOperation.class),
        @JsonSubTypes.Type(name = "SCALE", value = ApplicationScaleOperation.class),
        @JsonSubTypes.Type(name = "REPLACE_INSTANCES", value = ApplicationReplaceInstancesOperation.class),
        @JsonSubTypes.Type(name = "SUSPEND", value = ApplicationSuspendOperation.class),
        @JsonSubTypes.Type(name = "RECOVER", value = ApplicationRecoverOperation.class),
})
@Data
@Schema(description = "Application operation request. Discriminator: type",
        discriminatorProperty = "type",
        discriminatorMapping = {
            @DiscriminatorMapping(value = "CREATE", schema = ApplicationCreateOperation.class),
            @DiscriminatorMapping(value = "DESTROY", schema = ApplicationDestroyOperation.class),
            @DiscriminatorMapping(value = "START_INSTANCES", schema = ApplicationStartInstancesOperation.class),
            @DiscriminatorMapping(value = "STOP_INSTANCES", schema = ApplicationStopInstancesOperation.class),
            @DiscriminatorMapping(value = "SCALE", schema = ApplicationScaleOperation.class),
            @DiscriminatorMapping(value = "REPLACE_INSTANCES", schema = ApplicationReplaceInstancesOperation.class),
            @DiscriminatorMapping(value = "SUSPEND", schema = ApplicationSuspendOperation.class),
            @DiscriminatorMapping(value = "RECOVER", schema = ApplicationRecoverOperation.class)
        },
        subTypes = {
            ApplicationCreateOperation.class,
            ApplicationDestroyOperation.class,
            ApplicationStartInstancesOperation.class,
            ApplicationStopInstancesOperation.class,
            ApplicationScaleOperation.class,
            ApplicationReplaceInstancesOperation.class,
            ApplicationSuspendOperation.class,
            ApplicationRecoverOperation.class
        })
public abstract class ApplicationOperation {
    @Schema(description = "Operation type discriminator", example = "CREATE")
    private final ApplicationOperationType type;

    public abstract <T> T accept(final ApplicationOperationVisitor<T> visitor);
}
