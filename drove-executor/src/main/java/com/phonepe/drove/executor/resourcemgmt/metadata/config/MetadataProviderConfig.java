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

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.AccessLevel;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(
                name = MetadataProviderType.ENVIRONMENT, value = EnvironmentBasedMetadataProviderConfig.class),
        @JsonSubTypes.Type(
                name = MetadataProviderType.CONFIGURED, value = ConfiguredMetadataProviderConfig.class),
        @JsonSubTypes.Type(
                name = MetadataProviderType.DYNAMIC_COMMAND, value = DynamicCommandBasedMetadataProviderConfig.class),
})
@RequiredArgsConstructor(access = AccessLevel.PROTECTED)
@Data
public abstract class MetadataProviderConfig {

    private final String type;

    public abstract <T> T accept(final MetadataProviderConfigVisitor<T> visitor);

}
