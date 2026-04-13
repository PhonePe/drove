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

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.swagger.v3.oas.annotations.media.DiscriminatorMapping;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * Base class for logging configuration
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(name = "LOCAL", value = LocalLoggingSpec.class),
        @JsonSubTypes.Type(name = "RSYSLOG", value = RsyslogLoggingSpec.class)
})
@Data
@Schema(
    description = "Logging configuration for container output. Supports local file logging or remote rsyslog.",
    discriminatorProperty = "type",
    discriminatorMapping = {
        @DiscriminatorMapping(value = "LOCAL", schema = LocalLoggingSpec.class),
        @DiscriminatorMapping(value = "RSYSLOG", schema = RsyslogLoggingSpec.class)
    },
    subTypes = { LocalLoggingSpec.class, RsyslogLoggingSpec.class }
)
public abstract class LoggingSpec {
    @Schema(description = "Type of logging configuration", requiredMode = Schema.RequiredMode.REQUIRED)
    private final LoggingType type;

    protected LoggingSpec(LoggingType type) {
        this.type = type;
    }

    public abstract <T> T accept(final LoggingSpecVisitor<T> visitor);
}
