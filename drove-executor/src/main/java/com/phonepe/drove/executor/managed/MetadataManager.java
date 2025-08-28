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

package com.phonepe.drove.executor.managed;


import com.codahale.metrics.MetricRegistry;
import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.phonepe.drove.common.model.utils.Pair;
import com.phonepe.drove.executor.resourcemgmt.metadata.MetadataConfig;
import com.phonepe.drove.executor.resourcemgmt.metadata.filters.RegexMatchPredicate;
import com.phonepe.drove.executor.resourcemgmt.metadata.providers.MetadataProvider;
import io.dropwizard.lifecycle.Managed;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.reflections.Reflections;
import ru.vyarus.dropwizard.guice.module.installer.order.Order;

import javax.inject.Singleton;
import java.util.AbstractMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Slf4j
@Singleton
@Order(20)
public class MetadataManager implements Managed {

    @Value
    private static class VerifyChecksAndLog implements Predicate<Map.Entry<String, String>> {
        Predicate<Map.Entry<String, String>> underlyingCheck;
        String errorLogFormat;

        @Override
        public boolean test(Map.Entry<String, String> entry) {
            if (underlyingCheck.test(entry)) {
                log.error(errorLogFormat, entry.getKey());
                return false;
            }
            return true;
        }

    }

    private final Map<String, MetadataProvider> metadataProviders;
    private final Predicate<Map.Entry<String, String>> blacklistedFilter;
    private final int valueMaxLimit;


    @Inject
    @SuppressWarnings("unused")
    public MetadataManager(final MetadataConfig config, final MetricRegistry metricRegistry) {
        metadataProviders = createMetadataProviders(config, metricRegistry);
        this.valueMaxLimit = config.getValueMaxLimit();
        this.blacklistedFilter = new RegexMatchPredicate(config.getBlacklistedKeys());
    }

    @VisibleForTesting
    MetadataManager(
            final MetadataConfig config,
            final Map<String, MetadataProvider> metadataProviders) {
        this.metadataProviders = Objects.requireNonNullElse(metadataProviders, Map.of());
        this.valueMaxLimit = config.getValueMaxLimit();
        this.blacklistedFilter = new RegexMatchPredicate(config.getBlacklistedKeys());
    }


    @Override
    public void start() throws Exception {
        metadataProviders
                .forEach((name, provider) -> provider.start(name));
    }

    @Override
    public void stop() throws Exception {
        metadataProviders
                .forEach((name, provider) -> provider.stop());
    }

    public Map<String, String> fetchMetadata() {

        val conflicts = findConflictingKeys(metadataProviders);

        return metadataProviders
                .values()
                .stream()
                .map(MetadataProvider::metadata)
                .flatMap(map -> map.entrySet().stream())
                .filter(new VerifyChecksAndLog(blacklistedFilter, "Blacklisted Key '{}' ignored"))
                .filter(new VerifyChecksAndLog(entry -> entry.getValue().length() > valueMaxLimit,
                                               "Value for the key '{}' is higher than the allowed limit of " + valueMaxLimit + " is ignored"))
                .filter(entry -> !conflicts.contains(entry.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }


    private List<String> findConflictingKeys(Map<String, MetadataProvider> metadataProviders) {
        var conflicts = metadataProviders.entrySet()
                .stream()
                .flatMap(entry -> entry.getValue().metadata()
                        .keySet()
                        .stream()
                        .map(s -> new AbstractMap.SimpleEntry<>(s, entry.getKey())))
                .collect(Collectors.groupingBy(Map.Entry::getKey,
                                               Collectors.mapping(Map.Entry::getValue, Collectors.toList())))
                .entrySet().stream()
                .filter(entry -> entry.getValue().size() > 1)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        if (!conflicts.isEmpty()) {
            conflicts.forEach((key, value) -> {
                String providersList = value.stream()
                        .distinct()
                        .map(Object::toString)
                        .collect(Collectors.joining(", "));
                log.error("Duplicate key '{}' ignored, found in the providers {}", key, providersList);
            });

        }

        return conflicts.keySet().stream().toList();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, MetadataProvider> createMetadataProviders(
            MetadataConfig metadataConfig,
            MetricRegistry metricRegistry) {
        var providersFactoryAvailable = new Reflections("com.phonepe.drove")
                .getSubTypesOf(MetadataProvider.MetadataProviderFactory.class);
        return metadataConfig.getMetadataProviders()
                .entrySet()
                .stream()
                .map(providerConfigEntry -> {
                    String name = providerConfigEntry.getKey();
                    val cfg = providerConfigEntry.getValue();
                    val providerName = providerConfigEntry.getValue().getType();
                    val instance = providersFactoryAvailable.stream()
                            .filter(cls -> cls.getAnnotation(MetadataProvider.MetadataProviderNamed.class) != null)
                            .filter(cls -> cls.getAnnotation(MetadataProvider.MetadataProviderNamed.class)
                                    .value()
                                    .equals(providerName))
                            .map(cls -> {
                                try {
                                    val constructor = cls.getDeclaredConstructor();
                                    val factory = constructor.newInstance();
                                    return factory.create(metricRegistry, cfg);
                                }
                                catch (Exception e) {
                                    log.error("Error building metadata provider: %s".formatted(e.getMessage()), e);
                                    return null;
                                }
                            })
                            .filter(Objects::nonNull)
                            .findFirst();
                    if (instance.isEmpty()) {
                        throw new IllegalStateException("Unable to instantiate MetadataProvider with name: " + providerName
                                                                + ". Please ensure that the MetadataProvider is " +
                                                                "available in the class & MetadataProviderFactory is " +
                                                                "with @MetadataProviderNamed.");
                    }
                    return new Pair<>(name, instance.get());
                })
                .collect(Collectors.toMap(Pair::getFirst, Pair::getSecond));
    }

}
