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

package com.phonepe.drove.models.application.logging;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotEmpty;

/**
 *
 */
@Value
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Jacksonized
@Builder
public class LocalLoggingSpec extends LoggingSpec {
    public static final LoggingSpec DEFAULT = new LocalLoggingSpec("10m", 3, true);

    @NotEmpty(message = "- Specify max size of logs. Use 10m, 1g etc. Files bigger than this will be rotated")
    String maxSize;

    @Min(value = 1, message = "- At least one log will be present, so min value is 1")
    @Max(value = 100, message = "- Maximum 100 log files can be retained")
    int maxFiles;
    boolean compress;

    public LocalLoggingSpec(String maxSize, int maxFiles, boolean compress) {
        super(LoggingType.LOCAL);
        this.maxSize = maxSize;
        this.maxFiles = maxFiles;
        this.compress = compress;
    }

    @Override
    public <T> T accept(LoggingSpecVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
