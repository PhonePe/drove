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

package com.phonepe.drove.executor.resourcemgmt.metadata.providers;

import com.codahale.metrics.MetricRegistry;
import com.google.common.annotations.VisibleForTesting;
import com.phonepe.drove.executor.resourcemgmt.metadata.config.EnvironmentBasedMetadataProviderConfig;
import com.phonepe.drove.executor.resourcemgmt.metadata.config.MetadataProviderType;
import com.phonepe.drove.executor.resourcemgmt.metadata.filters.RegexMatchPredicate;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;


@Slf4j
public class EnvironmentBasedMetadataProvider implements MetadataProvider {

    @MetadataProviderNamed(MetadataProviderType.ENVIRONMENT)
    public static class EnviornmentBasedMetadataProviderFactory implements MetadataProviderFactory<EnvironmentBasedMetadataProviderConfig, EnvironmentBasedMetadataProvider> {

        @Override
        public EnvironmentBasedMetadataProvider create(
                final MetricRegistry metricRegistry,
                final EnvironmentBasedMetadataProviderConfig config) {
            return new EnvironmentBasedMetadataProvider(metricRegistry, config);
        }
    }

    private final Predicate<Map.Entry<String, String>> matcher;

    private final Supplier<Map<String, String>> dataProvider;

    @Inject
    public EnvironmentBasedMetadataProvider(
            final MetricRegistry metricRegistry,
            final EnvironmentBasedMetadataProviderConfig config) {
        this(metricRegistry, config, System::getenv);
    }

    @SuppressWarnings("unused")
    @VisibleForTesting
    public EnvironmentBasedMetadataProvider(
            final MetricRegistry metricRegistry,
            final EnvironmentBasedMetadataProviderConfig config,
            final Supplier<Map<String, String>> dataProvider) {
        this.matcher = new RegexMatchPredicate(config.getWhitelistedVariables());
        this.dataProvider = dataProvider;
    }

    @Override
    public Map<String, String> metadata() {
        return dataProvider.get().entrySet().stream()
                .filter(matcher)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }




}
