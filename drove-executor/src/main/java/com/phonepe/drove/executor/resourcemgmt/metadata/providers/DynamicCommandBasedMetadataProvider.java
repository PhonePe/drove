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
import com.codahale.metrics.Timer;
import com.google.common.annotations.VisibleForTesting;
import com.phonepe.drove.executor.resourcemgmt.metadata.config.CommandBasedConfig;
import com.phonepe.drove.executor.resourcemgmt.metadata.config.CommandType;
import com.phonepe.drove.executor.resourcemgmt.metadata.config.DynamicCommandBasedMetadataProviderConfig;
import com.phonepe.drove.executor.resourcemgmt.metadata.config.MetadataProviderType;
import io.appform.signals.signals.ScheduledSignal;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.Duration;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.codahale.metrics.MetricRegistry.name;

@Slf4j
public class DynamicCommandBasedMetadataProvider implements MetadataProvider {
    public static final String COMMAND_RERUN_TIMETAKEN = "reruntimetaken";
    public static final String COMMAND_RERUN_TIMEWINDOW = "reruntimewindow";
    public static final String COMMAND_REFRESHER_HANDLER_NAME = "DYNAMIC_COMMAND_REFRESHER_HANDLER";


    private final ScheduledSignal dynamicCommandRefresher;
    private final int configRefreshInSecs;
    private Timer commandRerunTimeTaken;
    private Timer commandRerunTimeWindow;
    private final MetricRegistry metricsRegistry;

    private final Map<String, String> staticMetadata;
    private final List<Map.Entry<String, CommandBasedConfig>> dynamicCommands;
    private final AtomicReference<Map<String, String>> dynamicConfigValuesRef = new AtomicReference<>(new HashMap<>());

    private long lastDynamicCommandSuccessfulEpochInMillis; // for observability

    @Inject
    public DynamicCommandBasedMetadataProvider(final MetricRegistry metricsRegistry, final DynamicCommandBasedMetadataProviderConfig dynamicCommandBasedMetadataProviderConfig) {

        this.metricsRegistry = metricsRegistry;

        staticMetadata = executeStatic(dynamicCommandBasedMetadataProviderConfig);

        dynamicCommands = dynamicCommandBasedMetadataProviderConfig
                .getCommands()
                .entrySet()
                .stream()
                .filter(e -> e.getValue().getType() == CommandType.DYNAMIC)
                .toList();

        dynamicCommandRefresher = new ScheduledSignal(
                                        Duration.ofSeconds(dynamicCommandBasedMetadataProviderConfig.getCommandRerunInSecs())
                                    );
        configRefreshInSecs = dynamicCommandBasedMetadataProviderConfig.getCommandRerunInSecs();
    }

    @Override
    public void start(String name) {
        commandRerunTimeTaken = namespacedMetricTimer(name, COMMAND_RERUN_TIMETAKEN);
        commandRerunTimeWindow = namespacedMetricTimer(name, COMMAND_RERUN_TIMEWINDOW);

        dynamicCommandRefresher.connect(COMMAND_REFRESHER_HANDLER_NAME, this::refreshDynamicCommands);
        refreshDynamicCommands(new Date());
    }

    private Timer namespacedMetricTimer(String suffix, String name) {
        return metricsRegistry.timer(
                name("com",
                "phonepe",
                "drove",
                "executor",
                "metadata",
                "dynamiccommandprovider",
                name,
                suffix)
        );
    }

    @Override
    public void stop() {
        dynamicCommandRefresher.disconnect(COMMAND_REFRESHER_HANDLER_NAME);
        dynamicCommandRefresher.close();
    }

    @Override
    public Map<String, String> metadata() {
        return Stream.of(staticMetadata, dynamicConfigValuesRef.get())
                .flatMap(map -> map.entrySet().stream())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    @VisibleForTesting
    void refreshDynamicCommands(Date date) {

        if (System.currentTimeMillis() - lastDynamicCommandSuccessfulEpochInMillis < configRefreshInSecs * 1000L) {
            log.warn("Skipping dynamic command refresh as last successful execution was within the configured interval");
            return;
        }

        if ( lastDynamicCommandSuccessfulEpochInMillis > 0 ) {
            var consecutiveRefreshWindow = System.currentTimeMillis() - lastDynamicCommandSuccessfulEpochInMillis;
            commandRerunTimeWindow.update(Duration.ofMillis(consecutiveRefreshWindow));
        }

        try ( var ctx = commandRerunTimeTaken.time() ) {
            Map<String, String> values = dynamicCommands
                    .stream()
                    .map(DynamicCommandBasedMetadataProvider::commandExecutor)
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

            dynamicConfigValuesRef.getAndSet(values);

            lastDynamicCommandSuccessfulEpochInMillis = System.currentTimeMillis();
        }
    }

    private Map<String, String> executeStatic(DynamicCommandBasedMetadataProviderConfig config) {
        return config
                .getCommands()
                .entrySet()
                .stream()
                .filter(e -> e.getValue().getType() == CommandType.STATIC)
                .map(DynamicCommandBasedMetadataProvider::commandExecutor)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    @VisibleForTesting
    @SuppressWarnings("java:S4036") // Suppress "Command injection" warning as we are executing commands that are configured and controlled by the user.
    static Map.Entry<String, String> commandExecutor(Map.Entry<String, CommandBasedConfig> cmdEntry) {
        var pb = new ProcessBuilder("bash", "-c", cmdEntry.getValue().getCommand());
        String result = cmdEntry.getValue().getDefaultValue();

        try {
            var process = pb.start();
            var reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            var errReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));

            String output = reader
                    .lines()
                    .collect(Collectors.joining(System.lineSeparator()));

            String error = errReader
                    .lines()
                    .collect(Collectors.joining(System.lineSeparator()));

            int exitCode = process.waitFor();
            if ( exitCode == 0) {
                result = output;
            }

            log.error("Command returned errored: '{}, exit code: {}, error: {} ignoring the command",
                    cmdEntry.getKey(),
                    exitCode,
                    error
            );
        } catch (Exception e) {
            log.error("Unable to execute command: '{}', ignoring the command",
                    cmdEntry.getKey()
            );
            if ( e instanceof InterruptedException ) {
                Thread.currentThread().interrupt();
            }
        }
        return Map.entry(cmdEntry.getKey(), result);
    }

    @MetadataProviderNamed(MetadataProviderType.DYNAMIC_COMMAND)
    public static class DynamicCommandBasedMetadataProviderFactory implements MetadataProviderFactory<DynamicCommandBasedMetadataProviderConfig, DynamicCommandBasedMetadataProvider> {

        @Override
        public DynamicCommandBasedMetadataProvider create(MetricRegistry metricRegistry, DynamicCommandBasedMetadataProviderConfig config) {
            return new DynamicCommandBasedMetadataProvider(metricRegistry, config);
        }
    }


}
