package com.phonepe.drove.executor.utils;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.model.*;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.phonepe.drove.common.CommonUtils;
import com.phonepe.drove.common.model.ApplicationInstanceSpec;
import com.phonepe.drove.common.model.DeploymentUnitSpec;
import com.phonepe.drove.common.model.DeploymentUnitSpecVisitor;
import com.phonepe.drove.common.model.TaskInstanceSpec;
import com.phonepe.drove.executor.ExecutorOptions;
import com.phonepe.drove.executor.dockerauth.DockerAuthConfig;
import com.phonepe.drove.executor.dockerauth.DockerAuthConfigVisitor;
import com.phonepe.drove.executor.resourcemgmt.ResourceConfig;
import com.phonepe.drove.models.application.MountedVolume;
import com.phonepe.drove.models.application.executable.DockerCoordinates;
import com.phonepe.drove.models.application.executable.ExecutableTypeVisitor;
import com.phonepe.drove.models.application.logging.LocalLoggingSpec;
import com.phonepe.drove.models.application.logging.LoggingSpecVisitor;
import com.phonepe.drove.models.application.logging.RsyslogLoggingSpec;
import com.phonepe.drove.models.info.resources.allocation.CPUAllocation;
import com.phonepe.drove.models.info.resources.allocation.MemoryAllocation;
import com.phonepe.drove.models.info.resources.allocation.ResourceAllocationVisitor;
import io.dropwizard.util.DataSize;
import lombok.SneakyThrows;
import lombok.Value;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.commons.lang3.StringUtils;

import java.util.*;
import java.util.function.Function;

import static com.phonepe.drove.common.CommonUtils.hostname;

/**
 *
 */
@UtilityClass
@Slf4j
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
        pullImageCmd.exec(new ImagePullProgressHandler(image)).awaitCompletion();
        val imageId = client.inspectImageCmd(image).exec().getId();
        log.info("Pulled image {} with image ID: {}", image, imageId);
        return responseHandler.apply(imageId);
    }

    public static void cleanupImage(final DockerClient dockerClient, final String dockerImageId) {
        dockerClient.removeImageCmd(dockerImageId)
                .withForce(true)
                .exec();
        log.info("Removed image: {}", dockerImageId);
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
            final ExecutorOptions executorOptions) {
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
                    .withMemorySwappiness(0L)
//                    .withOomKillDisable(true)
// There is a bug in docker. Enabling this leads to us not getting any stats
                    .withAutoRemove(autoRemove(deploymentUnitSpec))
                    .withLogConfig(logConfig(deploymentUnitSpec, logBufferSize, cacheFileSize, cachedFileCount))
                    .withUlimits(List.of(new Ulimit("nofile", maxOpenFiles, maxOpenFiles)));

            deploymentUnitSpec.getResources()
                    .forEach(resourceRequirement -> resourceRequirement.accept(new ResourceAllocationVisitor<Void>() {
                        @Override
                        public Void visit(CPUAllocation cpu) {
                            hostConfig.withCpuCount((long) cpu.getCores().size());
                            if (!resourceConfig.isDisableNUMAPinning()) {
                                hostConfig.withCpusetCpus(StringUtils.join(cpu.getCores()
                                                                                   .values()
                                                                                   .stream()
                                                                                   .flatMap(Set::stream)
                                                                                   .map(i -> Integer.toString(i))
                                                                                   .toList(),
                                                                           ","));
                            }
                            return null;
                        }

                        @Override
                        public Void visit(MemoryAllocation memory) {
                            hostConfig.withMemory(memory.getMemoryInMB()
                                                          .values()
                                                          .stream()
                                                          .mapToLong(Long::longValue)
                                                          .sum() * (1 << 20));
                            if (!resourceConfig.isDisableNUMAPinning()) {
                                hostConfig.withCpusetMems(StringUtils.join(memory.getMemoryInMB().keySet(), ","));
                            }
                            return null;
                        }
                    }));
            val exposedPorts = new ArrayList<ExposedPort>();
            val env = new ArrayList<String>();
            val hostName = hostname();
            env.add("HOST=" + hostName);
            if (null != deploymentUnitSpec.getVolumes()) {
                hostConfig.withBinds(deploymentUnitSpec.getVolumes()
                                             .stream()
                                             .map(volume -> new Bind(volume.getPathOnHost(),
                                                                     new Volume(
                                                                             com.google.common.base.Strings.isNullOrEmpty(
                                                                                     volume.getPathInContainer())
                                                                             ? volume.getPathOnHost()
                                                                             : volume.getPathInContainer()),
                                                                     volume.getMode()
                                                                             .equals(MountedVolume.MountMode.READ_ONLY)
                                                                     ? AccessMode.ro
                                                                     : AccessMode.rw))
                                             .toList());
            }

            val labels = new HashMap<String, String>();
            env.addAll(deploymentUnitSpec.getEnv()
                               .entrySet()
                               .stream()
                               .map(e -> e.getKey() + "=" + e.getValue())
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

    private static Boolean autoRemove(DeploymentUnitSpec deploymentUnitSpec) {
        return deploymentUnitSpec.accept(new DeploymentUnitSpecVisitor<>() {
            @Override
            public Boolean visit(ApplicationInstanceSpec instanceSpec) {
                return true;
            }

            @Override
            public Boolean visit(TaskInstanceSpec taskInstanceSpec) {
                return false;
            }
        });
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
                return new LogConfig(LogConfig.LoggingType.LOCAL, configBuilder.build());
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
