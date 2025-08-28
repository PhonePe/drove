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

package com.phonepe.drove.executor.resourcemgmt.metadata;

import com.phonepe.drove.executor.resourcemgmt.metadata.config.MetadataProviderConfig;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import javax.validation.Valid;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;

@Value
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Jacksonized
public class MetadataConfig {
    public static final MetadataConfig DEFAULT = new MetadataConfig();

    @Builder.Default
    Map<String, @NotNull @Valid MetadataProviderConfig> metadataProviders = Map.of();

    @Builder.Default
    @Min(1)
    int valueMaxLimit = 1024;

    @Builder.Default
    List<@NotNull String> blacklistedKeys = List.of();
}
