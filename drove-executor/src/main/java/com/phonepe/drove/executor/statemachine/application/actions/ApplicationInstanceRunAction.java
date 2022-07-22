package com.phonepe.drove.executor.statemachine.application.actions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.LogConfig;
import com.github.dockerjava.api.model.Ports;
import com.google.common.collect.ImmutableMap;
import com.phonepe.drove.common.model.ApplicationInstanceSpec;
import com.phonepe.drove.executor.engine.DockerLabels;
import com.phonepe.drove.executor.engine.InstanceLogHandler;
import com.phonepe.drove.executor.logging.LogBus;
import com.phonepe.drove.executor.model.ExecutorApplicationInstanceInfo;
import com.phonepe.drove.executor.resourcemgmt.ResourceConfig;
import com.phonepe.drove.executor.statemachine.application.ApplicationInstanceAction;
import com.phonepe.drove.executor.statemachine.InstanceActionContext;
import com.phonepe.drove.executor.utils.DockerUtils;
import com.phonepe.drove.models.application.JobType;
import com.phonepe.drove.models.application.logging.LocalLoggingSpec;
import com.phonepe.drove.models.application.logging.LoggingSpecVisitor;
import com.phonepe.drove.models.application.logging.RsyslogLoggingSpec;
import com.phonepe.drove.models.info.resources.allocation.ResourceAllocation;
import com.phonepe.drove.models.instance.InstancePort;
import com.phonepe.drove.models.instance.InstanceState;
import com.phonepe.drove.models.instance.LocalInstanceInfo;
import com.phonepe.drove.statemachine.StateData;
import io.appform.functionmetrics.MonitoredFunction;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.slf4j.MDC;

import javax.inject.Inject;
import java.net.ServerSocket;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static com.phonepe.drove.common.CommonUtils.hostname;

/**
 *
 */
@Slf4j
public class ApplicationInstanceRunAction extends ApplicationInstanceAction {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final LogBus logBus;
    private final ResourceConfig schedulingConfig;

    @Inject
    public ApplicationInstanceRunAction(LogBus logBus, ResourceConfig resourceConfig) {
        this.logBus = logBus;
        this.schedulingConfig = resourceConfig;
    }

    @Override
    @MonitoredFunction(method = "execute")
    protected StateData<InstanceState, ExecutorApplicationInstanceInfo> executeImpl(
            InstanceActionContext<ApplicationInstanceSpec> context, StateData<InstanceState, ExecutorApplicationInstanceInfo> currentState) {
        val instanceSpec = context.getInstanceSpec();
        val client = context.getClient();
        try {
            val instanceInfoRef = new AtomicReference<ExecutorApplicationInstanceInfo>();
            val containerId = DockerUtils.createContainer(
                    schedulingConfig,
                    client, instanceSpec.getAppId() + "-" + instanceSpec.getInstanceId(),
                    instanceSpec,
                    params -> {
                        val ports = new Ports();
                        val exposedPorts = params.getExposedPorts();
                        val portMappings = new HashMap<String, InstancePort>();
                        instanceSpec.getPorts().forEach(
                                portSpec -> {
                                    val freePort = findFreePort();
                                    val specPort = portSpec.getPort();
                                    val exposedPort = new ExposedPort(specPort);
                                    ports.bind(exposedPort, Ports.Binding.bindPort(freePort));
                                    exposedPorts.add(exposedPort);
                                    params.getCustomEnv().add(String.format("PORT_%d=%d", specPort, freePort));
                                    portMappings.put(portSpec.getName(),
                                                     new InstancePort(portSpec.getPort(),
                                                                      freePort,
                                                                      portSpec.getType()));
                                });
                        params.getHostConfig().withPortBindings(ports);
                        val instanceInfo = instanceInfo(currentState,
                                                        portMappings,
                                                        instanceSpec.getResources(),
                                                        params.getHostname(),
                                                        currentState.getData());
                        instanceInfoRef.set(instanceInfo);
                        val labels = params.getCustomLabels();
                        labels.put(DockerLabels.DROVE_JOB_TYPE_LABEL, JobType.SERVICE.name());
                        labels.put(DockerLabels.DROVE_INSTANCE_ID_LABEL, instanceSpec.getInstanceId());
                        labels.put(DockerLabels.DROVE_INSTANCE_SPEC_LABEL, MAPPER.writeValueAsString(instanceSpec));
                        labels.put(DockerLabels.DROVE_INSTANCE_DATA_LABEL, MAPPER.writeValueAsString(instanceInfo));

                        val env = params.getCustomEnv();
                        env.add("DROVE_INSTANCE_ID=" + instanceSpec.getInstanceId());
                        env.add("DROVE_EXECUTOR_HOST=" + hostname());
                        env.add("DROVE_APP_ID=" + instanceSpec.getAppId());
                        env.add("DROVE_APP_NAME=" + instanceSpec.getAppName());
                        env.add("DROVE_APP_INSTANCE_AUTH_TOKEN=" + instanceSpec.getInstanceAuthToken());
                    });

            context.setDockerInstanceId(containerId);
            client.startContainerCmd(containerId)
                    .exec();
            client.logContainerCmd(containerId)
                    .withTailAll()
                    .withFollowStream(true)
                    .withStdOut(true)
                    .withStdErr(true)
                    .exec(new InstanceLogHandler(MDC.getCopyOfContextMap(),
                                                 instanceSpec.getAppId(),
                                                 instanceSpec.getInstanceId(),
                                                 logBus));
            return StateData.create(InstanceState.UNREADY, instanceInfoRef.get());
        }
        catch (Exception e) {
            log.error("Error creating container: ", e);
            return StateData.errorFrom(currentState, InstanceState.START_FAILED, e.getMessage());
        }
    }

    @Override
    protected InstanceState defaultErrorState() {
        return InstanceState.START_FAILED;
    }

    private ExecutorApplicationInstanceInfo instanceInfo(
            StateData<InstanceState, ExecutorApplicationInstanceInfo> currentState,
            HashMap<String, InstancePort> portMappings,
            List<ResourceAllocation> resources,
            String hostName,
            ExecutorApplicationInstanceInfo oldData) {
        val data = currentState.getData();
        return new ExecutorApplicationInstanceInfo(
                data.getAppId(),
                data.getAppName(),
                data.getInstanceId(),
                data.getExecutorId(),
                new LocalInstanceInfo(hostName, portMappings),
                resources,
                Collections.emptyMap(),
                null == oldData
                ? new Date()
                : oldData.getCreated(),
                new Date());
    }

    @Override
    public void stop() {
        //Nothing to do here
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

    private LogConfig logConfig(final ApplicationInstanceSpec instanceSpec) {
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
