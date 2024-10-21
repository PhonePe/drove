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

package com.phonepe.drove.common.model.executor;

import com.phonepe.drove.common.model.ExecutorMessageType;
import com.phonepe.drove.common.model.LocalServiceInstanceSpec;
import com.phonepe.drove.common.model.MessageHeader;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;

/**
 *
 */
@Value
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class StartLocalServiceInstanceMessage extends ExecutorMessage {
    LocalServiceInstanceSpec spec;

    public StartLocalServiceInstanceMessage(
            MessageHeader header,
            ExecutorAddress address,
            LocalServiceInstanceSpec spec) {
        super(ExecutorMessageType.START_LOCAL_SERVICE_INSTANCE, header, address);
        this.spec = spec;
    }

    @Override
    public <T> T accept(ExecutorMessageVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
