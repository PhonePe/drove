/*
 *  Copyright (c) 2025 Original Author(s), PhonePe India Pvt. Ltd.
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

package com.phonepe.drove.executor.resourcemgmt.metadata.config;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import javax.validation.constraints.NotEmpty;
import java.util.List;

@Value
@Builder
@Jacksonized
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class EnvironmentBasedMetadataProviderConfig extends MetadataProviderConfig {

    @NotEmpty
    private List<@NotEmpty String> whitelistedVariables;

    public EnvironmentBasedMetadataProviderConfig(final List<String> whitelistedVariables) {
        super(MetadataProviderType.ENVIRONMENT);
        this.whitelistedVariables = whitelistedVariables;
    }

    @Override
    public <T> T accept(MetadataProviderConfigVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
