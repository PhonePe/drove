package com.phonepe.drove.executor.statemachine.actions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.dockerjava.api.model.*;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.phonepe.drove.common.CommonUtils;
import com.phonepe.drove.common.StateData;
import com.phonepe.drove.common.model.InstanceSpec;
import com.phonepe.drove.executor.engine.DockerLabels;
import com.phonepe.drove.executor.engine.InstanceLogHandler;
import com.phonepe.drove.executor.logging.LogBus;
import com.phonepe.drove.executor.model.ExecutorInstanceInfo;
import com.phonepe.drove.executor.statemachine.InstanceAction;
import com.phonepe.drove.executor.statemachine.InstanceActionContext;
import com.phonepe.drove.models.application.MountedVolume;
import com.phonepe.drove.models.application.executable.DockerCoordinates;
import com.phonepe.drove.models.application.logging.LocalLoggingSpec;
import com.phonepe.drove.models.application.logging.LoggingSpecVisitor;
import com.phonepe.drove.models.application.logging.RsyslogLoggingSpec;
import com.phonepe.drove.models.info.resources.allocation.CPUAllocation;
import com.phonepe.drove.models.info.resources.allocation.MemoryAllocation;
import com.phonepe.drove.models.info.resources.allocation.ResourceAllocation;
import com.phonepe.drove.models.info.resources.allocation.ResourceAllocationVisitor;
import com.phonepe.drove.models.instance.InstancePort;
import com.phonepe.drove.models.instance.InstanceState;
import com.phonepe.drove.models.instance.LocalInstanceInfo;
import io.appform.functionmetrics.MonitoredFunction;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.MDC;

import javax.inject.Inject;
import java.net.ServerSocket;
import java.util.*;
import java.util.stream.Collectors;

/**
 *
 */
@Slf4j
public class InstanceRunAction extends InstanceAction {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final LogBus logBus;

    @Inject
    public InstanceRunAction(LogBus logBus) {
        this.logBus = logBus;
    }

    @Override
    @MonitoredFunction(method = "execute")
    protected StateData<InstanceState, ExecutorInstanceInfo> executeImpl(
            InstanceActionContext context, StateData<InstanceState, ExecutorInstanceInfo> currentState) {
        val instanceSpec = context.getInstanceSpec();
        val client = context.getClient();
        val image = instanceSpec.getExecutable().accept(DockerCoordinates::getUrl);

        try (val containerCmd = client.createContainerCmd(image)) {
            containerCmd
                    .withName(instanceSpec.getAppId() + "-" + instanceSpec.getInstanceId());
            val hostConfig = new HostConfig()
                    .withMemorySwappiness(0L)
//                    .withOomKillDisable(true) //There is a bug in docker. Enabling this leads to us not getting any stats
                    .withAutoRemove(true)
                    .withLogConfig(logConfig(instanceSpec));

            instanceSpec.getResources()
                    .forEach(resourceRequirement -> resourceRequirement.accept(new ResourceAllocationVisitor<Void>() {
                        @Override
                        public Void visit(CPUAllocation cpu) {
                            hostConfig.withCpuCount((long) cpu.getCores().size());
                            hostConfig.withCpusetCpus(StringUtils.join(cpu.getCores()
                                                                               .values()
                                                                               .stream()
                                                                               .flatMap(Set::stream)
                                                                               .map(i -> Integer.toString(i))
                                                                               .collect(
                                                                                       Collectors.toUnmodifiableList()),
                                                                       ","));
                            return null;
                        }

                        @Override
                        public Void visit(MemoryAllocation memory) {
                            hostConfig.withMemory(memory.getMemoryInMB()
                                                          .values()
                                                          .stream()
                                                          .mapToLong(Long::longValue)
                                                          .sum() * (1 << 20));
                            hostConfig.withCpusetMems(StringUtils.join(memory.getMemoryInMB().keySet(), ","));
                            return null;
                        }
                    }));
            val ports = new Ports();
            val env = new ArrayList<String>();
            val portMappings = new HashMap<String, InstancePort>();
            val hostName = CommonUtils.hostname();
            env.add("HOST=" + hostName);
            instanceSpec.getPorts().forEach(
                    portSpec -> {
                        val freePort = findFreePort();
                        val specPort = portSpec.getPort();
                        ports.bind(new ExposedPort(specPort), Ports.Binding.bindPort(freePort));
                        env.add(String.format("PORT_%d=%d", specPort, freePort));
                        portMappings.put(portSpec.getName(),
                                         new InstancePort(portSpec.getPort(), freePort, portSpec.getType()));
                    });
            hostConfig.withPortBindings(ports);

            if (null != instanceSpec.getVolumes()) {
                hostConfig.withBinds(instanceSpec.getVolumes()
                                             .stream()
                                             .map(volume -> new Bind(volume.getPathOnHost(),
                                                                     new Volume(Strings.isNullOrEmpty(volume.getPathInContainer())
                                                                                ? volume.getPathOnHost()
                                                                                : volume.getPathInContainer()),
                                                                     volume.getMode()
                                                                             .equals(MountedVolume.MountMode.READ_ONLY)
                                                                     ? AccessMode.ro
                                                                     : AccessMode.rw))
                                             .collect(Collectors.toUnmodifiableList()));
            }
            val instanceInfo = instanceInfo(currentState, portMappings, instanceSpec.getResources(), hostName);
            val labels = new HashMap<String, String>();
            labels.put(DockerLabels.DROVE_INSTANCE_ID_LABEL, instanceSpec.getInstanceId());
            labels.put(DockerLabels.DROVE_INSTANCE_SPEC_LABEL, MAPPER.writeValueAsString(instanceSpec));
            labels.put(DockerLabels.DROVE_INSTANCE_DATA_LABEL, MAPPER.writeValueAsString(instanceInfo));
            env.addAll(instanceSpec.getEnv()
                               .entrySet()
                               .stream()
                               .map(e -> e.getKey() + "=" + e.getValue())
                               .collect(Collectors.toUnmodifiableList()));
            log.debug("Environment: {}", env);
            val id = containerCmd
                    .withHostConfig(hostConfig)
                    .withEnv(env)
                    .withLabels(labels)
                    .exec()
                    .getId();
            log.debug("Created container id: {}", id);

            context.setDockerInstanceId(id);
            client.startContainerCmd(id)
                    .exec();
            client.logContainerCmd(id)
                    .withTail(0)
                    .withFollowStream(true)
                    .withStdOut(true)
                    .withStdErr(true)
                    .exec(new InstanceLogHandler(MDC.getCopyOfContextMap(),
                                                 instanceSpec.getAppId(),
                                                 instanceSpec.getInstanceId(),
                                                 logBus));
            return StateData.create(InstanceState.UNREADY, instanceInfo);
        }
        catch (Exception e) {
            log.error("Error creating container: ", e);
            return StateData.errorFrom(currentState, InstanceState.START_FAILED, e.getMessage());
        }
    }

    private ExecutorInstanceInfo instanceInfo(
            StateData<InstanceState, ExecutorInstanceInfo> currentState,
            HashMap<String, InstancePort> portMappings,
            List<ResourceAllocation> resources,
            String hostName) {
        val data = currentState.getData();
        return new ExecutorInstanceInfo(
                data.getAppId(),
                data.getAppName(),
                data.getInstanceId(),
                data.getExecutorId(),
                new LocalInstanceInfo(hostName, portMappings),
                resources,
                Collections.emptyMap(),
                new Date(),
                new Date());
    }

    @Override
    public void stop() {

    }

    private int findFreePort() {
        /*//IANA recommended range
        IntStream.rangeClosed(49152, 65535)
                .filter(port -> try(val s ))*/
        try (val s = new ServerSocket(0)) {
            return s.getLocalPort();
        }
        catch (Exception e) {
            log.error("Port allocation failure");
        }
        return 0;
    }

    /*
    new LogConfig(LogConfig.LoggingType.SYSLOG)
                                           /*.setConfig(Map.of("mode", "non-blocking", //TODO::READ SIZE ETC FROM CONFIG
                                                             "max-size", "10m",
                                                             "max-file", "3",
                                                             "compress", "true"))*/

    private LogConfig logConfig(final InstanceSpec instanceSpec) {
        val spec = instanceSpec.getLoggingSpec() == null
                   ? LocalLoggingSpec.DEFAULT
                   : instanceSpec.getLoggingSpec();
        val configBuilder = ImmutableMap.<String, String>builder()
                .put("mode", "non-blocking")
                .put("max-buffer-size", "10m");
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
                        + instanceSpec.getAppName()
                        + Objects.requireNonNullElse(rsyslog.getTagSuffix(), ""));
                configBuilder.put("syslog-facility", "daemon");
                return new LogConfig(LogConfig.LoggingType.SYSLOG, configBuilder.build());
            }
        });
    }
}
