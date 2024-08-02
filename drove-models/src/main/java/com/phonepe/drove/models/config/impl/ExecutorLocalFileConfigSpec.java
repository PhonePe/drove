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

package com.phonepe.drove.models.config.impl;

import com.phonepe.drove.models.config.ConfigSpec;
import com.phonepe.drove.models.config.ConfigSpecType;
import com.phonepe.drove.models.config.ConfigSpecVisitor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

/**
 * File will be mounted from a path on executor directly
 */
@Value
@EqualsAndHashCode(callSuper = true)
@ToString
public class ExecutorLocalFileConfigSpec extends ConfigSpec {

    String filePathOnHost;

    @Jacksonized
    @Builder
    public ExecutorLocalFileConfigSpec(String localFilename, String filePathOnHost) {
        super(ConfigSpecType.EXECUTOR_LOCAL_FILE, localFilename);
        this.filePathOnHost = filePathOnHost;
    }

    @Override
    public <T> T accept(ConfigSpecVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
