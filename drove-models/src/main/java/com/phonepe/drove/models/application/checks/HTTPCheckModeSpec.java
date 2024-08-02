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

package com.phonepe.drove.models.application.checks;

import com.phonepe.drove.models.common.HTTPVerb;
import com.phonepe.drove.models.common.Protocol;
import io.dropwizard.util.Duration;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.Set;

/**
 *
 */
@Value
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Jacksonized
@Builder
public class HTTPCheckModeSpec extends CheckModeSpec {

    Protocol protocol;
    @NotEmpty
    String portName;
    @NotEmpty
    String path;
    @NotNull
    HTTPVerb verb;
    @NotEmpty
    @NotNull
    Set<Integer> successCodes;
    String payload;
    Duration connectionTimeout;
    boolean insecure;

    @SuppressWarnings("java:S107")
    public HTTPCheckModeSpec(
            Protocol protocol,
            String portName,
            String path,
            HTTPVerb verb,
            Set<Integer> successCodes,
            String payload,
            Duration connectionTimeout,
            boolean insecure) {
        super(CheckMode.HTTP);
        this.protocol = protocol;
        this.portName = portName;
        this.path = path;
        this.verb = verb;
        this.successCodes = successCodes;
        this.payload = payload;
        this.connectionTimeout = connectionTimeout;
        this.insecure = insecure;
    }

    @Override
    public <T> T accept(CheckModeSpecVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
