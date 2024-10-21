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

import com.phonepe.drove.models.operation.LocalServiceOperation;
import com.phonepe.drove.models.operation.LocalServiceOperationType;
import com.phonepe.drove.models.operation.LocalServiceOperationVisitor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;
import org.hibernate.validator.constraints.Range;

import javax.validation.constraints.NotEmpty;

/**
 * Scale number of service instances on executors
 */
@Value
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Jacksonized
@Builder
public class LocalServiceScaleOperation extends LocalServiceOperation {
    @NotEmpty
    String serviceId;

    @Range(min = 1, max = 256)
    int instancesPerHost;

    public LocalServiceScaleOperation(
            String serviceId,
            int instancesPerHost) {
        super(LocalServiceOperationType.SCALE);
        this.serviceId = serviceId;
        this.instancesPerHost = instancesPerHost;
    }

    @Override
    public <T> T accept(LocalServiceOperationVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
