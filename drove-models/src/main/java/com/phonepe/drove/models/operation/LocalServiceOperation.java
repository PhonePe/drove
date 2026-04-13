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
import io.swagger.v3.oas.annotations.media.DiscriminatorMapping;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * Base class for local service operations. Local services run on all executor nodes.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(name = "CREATE", value = LocalServiceCreateOperation.class),
        @JsonSubTypes.Type(name = "ACTIVATE", value = LocalServiceActivateOperation.class),
        @JsonSubTypes.Type(name = "DEACTIVATE", value = LocalServiceDeactivateOperation.class),
        @JsonSubTypes.Type(name = "STOP", value = LocalServiceDeactivateOperation.class),
        @JsonSubTypes.Type(name = "RESTART", value = LocalServiceRestartOperation.class),
        @JsonSubTypes.Type(name = "ADJUST_INSTANCES", value = LocalServiceAdjustInstancesOperation.class),
        @JsonSubTypes.Type(name = "DEPLOY_TEST_INSTANCE", value = LocalServiceDeployTestInstanceOperation.class),
        @JsonSubTypes.Type(name = "REPLACE_INSTANCES", value = LocalServiceReplaceInstancesOperation.class),
        @JsonSubTypes.Type(name = "STOP_INSTANCES", value = LocalServiceStopInstancesOperation.class),
        @JsonSubTypes.Type(name = "UPDATE_INSTANCE_COUNT", value = LocalServiceUpdateInstanceCountOperation.class),
        @JsonSubTypes.Type(name = "DESTROY", value = LocalServiceDestroyOperation.class),
})
@Data
@Schema(
        description = "Base class for local service operations. Local services are deployed on all executor nodes.",
        discriminatorProperty = "type",
        discriminatorMapping = {
                @DiscriminatorMapping(value = "CREATE", schema = LocalServiceCreateOperation.class),
                @DiscriminatorMapping(value = "ACTIVATE", schema = LocalServiceActivateOperation.class),
                @DiscriminatorMapping(value = "DEACTIVATE", schema = LocalServiceDeactivateOperation.class),
                @DiscriminatorMapping(value = "STOP", schema = LocalServiceDeactivateOperation.class),
                @DiscriminatorMapping(value = "RESTART", schema = LocalServiceRestartOperation.class),
                @DiscriminatorMapping(value = "ADJUST_INSTANCES", schema = LocalServiceAdjustInstancesOperation.class),
                @DiscriminatorMapping(value = "DEPLOY_TEST_INSTANCE", schema = LocalServiceDeployTestInstanceOperation.class),
                @DiscriminatorMapping(value = "REPLACE_INSTANCES", schema = LocalServiceReplaceInstancesOperation.class),
                @DiscriminatorMapping(value = "STOP_INSTANCES", schema = LocalServiceStopInstancesOperation.class),
                @DiscriminatorMapping(value = "UPDATE_INSTANCE_COUNT", schema = LocalServiceUpdateInstanceCountOperation.class),
                @DiscriminatorMapping(value = "DESTROY", schema = LocalServiceDestroyOperation.class)
        },
        subTypes = {
                LocalServiceCreateOperation.class,
                LocalServiceActivateOperation.class,
                LocalServiceDeactivateOperation.class,
                LocalServiceRestartOperation.class,
                LocalServiceAdjustInstancesOperation.class,
                LocalServiceDeployTestInstanceOperation.class,
                LocalServiceReplaceInstancesOperation.class,
                LocalServiceStopInstancesOperation.class,
                LocalServiceUpdateInstanceCountOperation.class,
                LocalServiceDestroyOperation.class
        }
)
public abstract class LocalServiceOperation {
    @Schema(description = "Type of local service operation", requiredMode = Schema.RequiredMode.REQUIRED)
    private final LocalServiceOperationType type;

    public abstract <T> T accept(final LocalServiceOperationVisitor<T> visitor);
}
