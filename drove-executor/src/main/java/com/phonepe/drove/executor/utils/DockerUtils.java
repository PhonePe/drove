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

package com.phonepe.drove.executor.utils;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallbackTemplate;
import com.github.dockerjava.api.command.PullImageCmd;
import com.github.dockerjava.api.model.*;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.phonepe.drove.common.CommonUtils;
import com.phonepe.drove.common.coverageutils.IgnoreInJacocoGeneratedReport;
import com.phonepe.drove.common.model.DeploymentUnitSpec;
import com.phonepe.drove.common.net.HttpCaller;
import com.phonepe.drove.executor.ExecutorOptions;
import com.phonepe.drove.executor.dockerauth.DockerAuthConfig;
import com.phonepe.drove.executor.dockerauth.DockerAuthConfigVisitor;
import com.phonepe.drove.executor.resourcemgmt.ResourceConfig;
import com.phonepe.drove.executor.resourcemgmt.ResourceManager;
import com.phonepe.drove.models.application.MountedVolume;
import com.phonepe.drove.models.application.executable.DockerCoordinates;
import com.phonepe.drove.models.application.executable.ExecutableTypeVisitor;
import com.phonepe.drove.models.application.logging.LocalLoggingSpec;
import com.phonepe.drove.models.application.logging.LoggingSpecVisitor;
import com.phonepe.drove.models.application.logging.RsyslogLoggingSpec;
import com.phonepe.drove.models.config.impl.InlineConfigSpec;
import com.phonepe.drove.models.info.resources.allocation.CPUAllocation;
import com.phonepe.drove.models.info.resources.allocation.MemoryAllocation;
import com.phonepe.drove.models.info.resources.allocation.ResourceAllocationVisitor;
import io.dropwizard.util.DataSize;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.lang3.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static com.phonepe.drove.common.CommonUtils.hostname;

/**
 *
 */
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class DockerUtils {
    public static <T> T pullImage(
            final DockerClient client,
            final DockerAuthConfig dockerAuthConfig,
            final DeploymentUnitSpec unitSpec,
            final Function<String, T> responseHandler) throws InterruptedException {
        val image = unitSpec.getExecutable().accept(DockerCoordinates::getUrl);
        log.info("Pulling docker image: {}", image);
        val pullImageCmd = client.pullImageCmd(image);
        if (null == dockerAuthConfig) {
            log.debug("No docker auth specified");
        }
        else {
            populateDockerRegistryAuth(dockerAuthConfig, pullImageCmd);
        }
        pullImageCmd.exec(new ImagePullProgressHandler(image)).awaitCompletion();
        val imageId = client.inspectImageCmd(image).exec().getId();
        log.info("Pulled image {} with image ID: {}", image, imageId);
        return responseHandler.apply(imageId);
    }

    public static void populateDockerRegistryAuth(DockerAuthConfig dockerAuthConfig, PullImageCmd pullImageCmd) {
        val authEntries = dockerAuthConfig.getEntries();
        val imageUri = Objects.requireNonNullElse(pullImageCmd.getRepository(), "");
        val registry = imageUri.substring(0, imageUri.indexOf("/"));
        if (!Strings.isNullOrEmpty(registry)) {
            log.info("Docker image registry auth lookup: {}", registry);
            if (authEntries.containsKey(registry)) {
                val ac = new AuthConfig();
                authEntries.get(registry).accept((DockerAuthConfigVisitor<Void>) credentials -> {
                    ac.withUsername(credentials.getUsername()).withPassword(credentials.getPassword());
                    return null;
                });
                pullImageCmd.withAuthConfig(ac);
                log.info("Docker auth setup for registry: {}", registry);
            }
            else {
                log.info("No auth info found for registry: {}", registry);
            }
        }
    }

    public static void cleanupContainer(final DockerClient dockerClient, final String containerId) {
        try (val cmd = dockerClient.removeContainerCmd(containerId)
                .withForce(true)
                .withRemoveVolumes(true)) {
            cmd.exec();
            log.info("Removed container: {}", containerId);
        }
    }

    public static void cleanupImage(final DockerClient dockerClient, final String dockerImageId) {
        try (val cmd = dockerClient.removeImageCmd(dockerImageId)) {
            cmd.exec();
            log.info("Removed image: {}", dockerImageId);
        }
    }

    public static boolean isRunning(final DockerClient dockerClient, final Container container) {
        try (val cmd = dockerClient.inspectContainerCmd(container.getId())) {
            return Boolean.TRUE.equals(cmd.exec()
                                               .getState()
                                               .getRunning());
        }
        catch (Exception e) {
            log.error("Error running inspect on " + container.getId(), e);
        }
        return false;
    }

    @Value
    public static class DockerRunParams {
        String hostname;
        HostConfig hostConfig;
        List<ExposedPort> exposedPorts;
        Map<String, String> customLabels;
        List<String> customEnv;
    }

    @FunctionalInterface
    public interface DockerCreateParmAugmenter {
        @SuppressWarnings("java:S112")
        void augment(final DockerRunParams params) throws Exception;
    }

    @SneakyThrows
    public static String createContainer(
            final ResourceConfig resourceConfig,
            final DockerClient client,
            final String id,
            final DeploymentUnitSpec deploymentUnitSpec,
            final DockerCreateParmAugmenter augmenter,
            final ExecutorOptions executorOptions,
            final ResourceManager resourceManager) {
        val image = deploymentUnitSpec.getExecutable().accept(DockerCoordinates::getUrl);

        try (val containerCmd = client.createContainerCmd(image)) {
            containerCmd
                    .withName(id);
            val maxOpenFiles = executorOptions.getMaxOpenFiles() <= 0
                               ? ExecutorOptions.DEFAULT_MAX_OPEN_FILES
                               : executorOptions.getMaxOpenFiles();
            val cachedFileCount = executorOptions.getCacheFileCount() <= 0
                                  ? ExecutorOptions.DEFAULT_LOG_CACHE_COUNT
                                  : executorOptions.getCacheFileCount();
            val logBufferSize = Objects.requireNonNullElse(executorOptions.getLogBufferSize(),
                                                           ExecutorOptions.DEFAULT_LOG_BUFFER_SIZE);
            val cacheFileSize = Objects.requireNonNullElse(executorOptions.getCacheFileSize(),
                                                           ExecutorOptions.DEFAULT_LOG_CACHE_SIZE);
            val hostConfig = new HostConfig()
                    .withLogConfig(logConfig(deploymentUnitSpec, logBufferSize, cacheFileSize, cachedFileCount))
                    .withUlimits(List.of(new Ulimit("nofile", maxOpenFiles, maxOpenFiles)));

            // This makes all available GPUs available to the containers running on the executor
            // This won't break anything if there are no GPUs at all and the config is tuned on; not recommended to
            // enable this on non GPU machines
            // No discovery of GPUs, managing/rationing of GPU devices
            // So this needs to be used in conjunction with tagging to ensure that only applications which require
            // GPU end up on executors with GPU enabled
            if (resourceConfig.isEnableNvidiaGpu()) {
                // This strange request is equivalent to 'docker create --gpus all'
                final DeviceRequest nvidiaGpuDeviceRequest = new DeviceRequest()
                        .withDriver("nvidia")
                        .withCount(-1)
                        .withCapabilities(List.of(List.of("gpu")))
                        .withDeviceIds(Collections.emptyList());
                hostConfig.withDeviceRequests(List.of(nvidiaGpuDeviceRequest));
            }

            populateResourceRequirements(resourceConfig, deploymentUnitSpec, hostConfig, resourceManager);
            val exposedPorts = new ArrayList<ExposedPort>();
            val env = new ArrayList<String>();
            val hostName = hostname();
            env.add("HOST=" + hostName);
            if (null != deploymentUnitSpec.getVolumes()) {
                populateVolumeMounts(deploymentUnitSpec, hostConfig);
            }

            val labels = new HashMap<String, String>();
            //Add all env with values
            env.addAll(deploymentUnitSpec.getEnv()
                               .entrySet()
                               .stream()
                               .map(e -> {
                                   if (Strings.isNullOrEmpty(e.getValue())) {
                                       val value = System.getenv(e.getKey());
                                       if (Strings.isNullOrEmpty(value)) {
                                           log.warn("No local value found for empty variable: {}. Nothing will be set.",
                                                    e.getKey());
                                           return null;
                                       }
                                       else {
                                           return e.getKey() + "=" + value;
                                       }
                                   }
                                   else {
                                       return e.getKey() + "=" + e.getValue();
                                   }
                               })
                               .filter(Objects::nonNull)
                               .toList());
            deploymentUnitSpec.getExecutable().accept((ExecutableTypeVisitor<Void>) dockerCoordinates -> {
                env.add("DROVE_CONTAINER_ID=" + dockerCoordinates.getUrl());
                return null;
            });
            val params = new DockerRunParams(hostName, hostConfig, exposedPorts, labels, env);
            augmenter.augment(params);
            log.debug("Environment: {}", env);
            val containerId = containerCmd
                    .withHostConfig(hostConfig)
                    .withEnv(env)
                    .withLabels(labels)
                    .withExposedPorts(exposedPorts)
                    .exec()
                    .getId();
            log.debug("Created container id: {}", containerId);
            return containerId;
        }
    }


    /**
     * This will copy resources to container without creating tmp files.
     * This ensures that residual files are not leaked outside
     * The downside is that this will take memory
     *
     * @param containerId        ID of the created container
     * @param client             Docker client
     * @param deploymentUnitSpec Spec for deployable
     * @param httpCaller         HTP caller for remote task
     */
    @SneakyThrows
    public static void injectConfigs(
            final String containerId,
            final DockerClient client,
            final DeploymentUnitSpec deploymentUnitSpec,
            final HttpCaller httpCaller) {
        val configs = deploymentUnitSpec.getConfigs();
        if (configs == null || configs.isEmpty()) {
            log.info("No configs to be injected to container");
            return;
        }
        val configSpecs = ExecutorUtils.translateConfigSpecs(configs, httpCaller);
        try (val bos = new ByteArrayOutputStream()) {
            try (val tos = new TarArchiveOutputStream(bos)) {
                tos.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
                tos.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_POSIX);
                configSpecs
                        .stream()
                        .map(InlineConfigSpec.class::cast)
                        .forEach(configSpec -> {
                            try {
                                val entry = new TarArchiveEntry(configSpec.getLocalFilename());
                                entry.setSize(configSpec.getData().length);
                                tos.putArchiveEntry(entry);
                                tos.write(configSpec.getData());
                                tos.closeArchiveEntry();
                            }
                            catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        });
                tos.flush();
                tos.finish();
            }
            client.copyArchiveToContainerCmd(containerId)
                    .withTarInputStream(new ByteArrayInputStream(bos.toByteArray()))
                    .exec();
        }
    }

    @Value
    public static class CommandOutput {
        long status;
        String output;
        String errorMessage;
    }

    @SneakyThrows
    public static CommandOutput runCommandInContainer(
            final String containerId,
            final DockerClient client,
            final String command) {
        val execId = client.execCreateCmd(containerId)
                .withAttachStderr(true)
                .withAttachStdout(true)
                .withCmd("sh", "-c", command)
                .exec()
                .getId();
        val buffer = new StringBuffer();
        val errMsg = new AtomicReference<String>();

        client.execStartCmd(execId)
                .exec(new ResultCallbackTemplate<ResultCallbackTemplate<?, Frame>, Frame>() {

                    @Override
                    public void onNext(Frame frame) {
                        switch (frame.getStreamType()) {
                            case STDOUT, STDERR, RAW -> buffer.append(new String(frame.getPayload()));
                            case STDIN -> log.error("Received frame of unsupported stream type: {}",
                                                    frame.getStreamType());
                            default -> log.error("Unexpected stream type value: {}", frame.getStreamType());
                        }
                    }

                    @Override
                    @IgnoreInJacocoGeneratedReport
                    public void onError(Throwable throwable) {
                        log.error("Error executing command: " + throwable.getMessage(), throwable);
                        errMsg.set(throwable.getMessage());
                        super.onError(throwable);
                    }
                })
                .awaitCompletion();
        val exitCode = client.inspectExecCmd(execId)
                .exec()
                .getExitCodeLong();
        return new CommandOutput(exitCode, buffer.toString(), errMsg.get());
    }

    private static void populateVolumeMounts(DeploymentUnitSpec deploymentUnitSpec, HostConfig hostConfig) {
        hostConfig.withBinds(deploymentUnitSpec.getVolumes()
                                     .stream()
                                     .map(volume -> new Bind(volume.getPathOnHost(),
                                                             new Volume(
                                                                     Strings.isNullOrEmpty(
                                                                             volume.getPathInContainer())
                                                                     ? volume.getPathOnHost()
                                                                     : volume.getPathInContainer()),
                                                             volume.getMode()
                                                                     .equals(MountedVolume.MountMode.READ_ONLY)
                                                             ? AccessMode.ro
                                                             : AccessMode.rw))
                                     .toList());
    }

    private static void populateResourceRequirements(
            ResourceConfig resourceConfig,
            DeploymentUnitSpec deploymentUnitSpec,
            HostConfig hostConfig,
            ResourceManager resourceManager) {
        deploymentUnitSpec.getResources()
                .forEach(resourceRequirement -> resourceRequirement.accept(new ResourceAllocationVisitor<Void>() {
                    @Override
                    public Void visit(CPUAllocation cpu) {
                        setupCPUParams(resourceConfig, hostConfig, resourceManager, cpu);
                        return null;
                    }

                    @Override
                    public Void visit(MemoryAllocation memory) {
                        hostConfig.withMemory(memory.getMemoryInMB()
                                                      .values()
                                                      .stream()
                                                      .mapToLong(Long::longValue)
                                                      .sum() * (1 << 20));
                        //Memory pinning is not done in case NUMA pinning is turned off
                        if (!resourceConfig.isDisableNUMAPinning()) {
                            hostConfig.withCpusetMems(StringUtils.join(memory.getMemoryInMB().keySet(), ","));
                        }
                        return null;
                    }
                }));
    }

    private static void setupCPUParams(ResourceConfig resourceConfig,
                                       HostConfig hostConfig,
                                       ResourceManager resourceManager,
                                       CPUAllocation cpu) {
        val requestedCores = cpu.getCores();
        val numCoresRequested = requestedCores
                .values()
                .stream()
                .map(Set::size)
                .mapToInt(Integer::intValue)
                .sum();
        hostConfig.withCpuCount((long) numCoresRequested);
        //If over-scaling is enabled,
        // We take list of physical cores and randomly map the processor to some cores
        // The mapping will be node to node. If 5 vcores are requested from node 1, we shall map
        // it to 5 physical cores on node 1. If this is not possible, we shall attempt to map the remaining
        // from other nodes
        val cpuset = new ArrayList<Integer>();
        if (resourceConfig.getOverProvisioning().isEnabled()) {
            val cores = resourceManager.currentState()
                    .getPhysicalLayout()
                    .getCores();
            requestedCores.forEach((numaNode, coresForNode) -> {
                val physicalCoresForNode = new ArrayList<>(cores.get(numaNode));
                val numCoresRequestedForThisNode = coresForNode.size();
                if (physicalCoresForNode.size() < numCoresRequestedForThisNode) {
                    log.warn("Available physical core count {} on node {} is less than number of cores requested {}.",
                            physicalCoresForNode.size(), numaNode, numCoresRequestedForThisNode);
                }
                else {
                    Collections.shuffle(physicalCoresForNode);
                    cpuset.addAll(physicalCoresForNode.subList(0, numCoresRequestedForThisNode));
                }
            });
            if (cpuset.size() < numCoresRequested) {
                // Looks like we could not find enough physical cores on this node to map
                // Will take a few from other nodes
                val alreadyMappedCores = Set.copyOf(cpuset);
                val remaining = numCoresRequested - cpuset.size();
                val physicalCores = new ArrayList<>(cores
                                                            .values()
                                                            .stream()
                                                            .flatMap(Set::stream)
                                                            .filter(pCore -> !alreadyMappedCores.contains(pCore))
                                                            .toList());
                Collections.shuffle(physicalCores);
                cpuset.addAll(physicalCores.subList(0, Math.min(physicalCores.size(), remaining)));
            }
        }
        else {
            cpuset.addAll(requestedCores
                                  .values()
                                  .stream()
                                  .flatMap(Set::stream)
                                  .toList());
        }
        if (cpuset.size() < numCoresRequested) {
            log.warn("Looks like we could not map all requested vcores to physical cores. " +
                             "Cpuset will not be called. Requested: {} Actual: {}",
                     numCoresRequested, cpuset.size());
        }
        else {
            hostConfig.withCpusetCpus(StringUtils.join(cpuset, ","));
        }
    }

    private static LogConfig logConfig(
            final DeploymentUnitSpec deploymentUnitSpec,
            final DataSize logBufferSize,
            final DataSize cacheFileSize,
            final int cacheFileCount) {
        val spec = deploymentUnitSpec.getLoggingSpec() == null
                   ? LocalLoggingSpec.DEFAULT
                   : deploymentUnitSpec.getLoggingSpec();
        val maxBufferMB = Math.max(logBufferSize.toMegabytes(), 1L);
        val configBuilder = ImmutableMap.<String, String>builder()
                .put("cache-disabled", "false")
                .put("cache-max-size", cacheFileSize.toMegabytes() + "m")
                .put("cache-max-file", Integer.toString(cacheFileCount))
                .put("cache-compress", "true")
                .put("mode", "non-blocking")
                .put("max-buffer-size",  maxBufferMB + "m");
        return spec.accept(new LoggingSpecVisitor<>() {
            @Override
            public LogConfig visit(LocalLoggingSpec local) {
                configBuilder.put("max-size", local.getMaxSize());
                configBuilder.put("max-file", Integer.toString(local.getMaxFiles()));
                configBuilder.put("compress", Boolean.toString(local.isCompress()));
                return new LogConfig(LogConfig.LoggingType.JSON_FILE, configBuilder.build());
            }

            @Override
            public LogConfig visit(RsyslogLoggingSpec rsyslog) {
                configBuilder.put("syslog-address", rsyslog.getServer());
                configBuilder.put("tag", Objects.requireNonNullElse(rsyslog.getTagPrefix(), "")
                        + CommonUtils.instanceId(deploymentUnitSpec)
                        + Objects.requireNonNullElse(rsyslog.getTagSuffix(), ""));
                configBuilder.put("syslog-facility", "daemon");
                return new LogConfig(LogConfig.LoggingType.SYSLOG, configBuilder.build());
            }
        });
    }
}
