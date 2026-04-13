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

package com.phonepe.drove.models.application.logging;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotEmpty;

/**
 * Local file-based logging with rotation
 */
@Value
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Jacksonized
@Builder
@Schema(description = "Local file-based logging configuration with log rotation support")
public class LocalLoggingSpec extends LoggingSpec {
    public static final LoggingSpec DEFAULT = new LocalLoggingSpec("10m", 3, true);

    @NotEmpty(message = "- Specify max size of logs. Use 10m, 1g etc. Files bigger than this will be rotated")
    @Schema(description = "Maximum size of log files before rotation (e.g., '10m', '1g')", example = "10m", requiredMode = Schema.RequiredMode.REQUIRED)
    String maxSize;

    @Min(value = 1, message = "- At least one log will be present, so min value is 1")
    @Max(value = 100, message = "- Maximum 100 log files can be retained")
    @Schema(description = "Maximum number of log files to retain after rotation", example = "3", minimum = "1", maximum = "100")
    int maxFiles;

    @Schema(description = "Whether to compress rotated log files", example = "true")
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
