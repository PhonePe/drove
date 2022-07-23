package com.phonepe.drove.executor.utils;

import com.google.common.base.Strings;
import com.phonepe.drove.executor.checker.Checker;
import com.phonepe.drove.executor.checker.HttpChecker;
import com.phonepe.drove.executor.model.DeployedExecutorInstanceInfo;
import com.phonepe.drove.executor.model.DeployedExecutorInstanceInfoVisitor;
import com.phonepe.drove.executor.model.ExecutorApplicationInstanceInfo;
import com.phonepe.drove.executor.model.ExecutorTaskInstanceInfo;
import com.phonepe.drove.executor.resourcemgmt.ResourceInfo;
import com.phonepe.drove.models.application.checks.CheckModeSpecVisitor;
import com.phonepe.drove.models.application.checks.CheckSpec;
import com.phonepe.drove.models.application.checks.CmdCheckModeSpec;
import com.phonepe.drove.models.application.checks.HTTPCheckModeSpec;
import com.phonepe.drove.models.info.ExecutorResourceSnapshot;
import com.phonepe.drove.models.instance.InstanceInfo;
import com.phonepe.drove.models.instance.InstanceState;
import com.phonepe.drove.models.taskinstance.TaskInstanceInfo;
import com.phonepe.drove.models.taskinstance.TaskInstanceState;
import com.phonepe.drove.statemachine.StateData;
import lombok.experimental.UtilityClass;
import lombok.val;
import org.apache.commons.lang3.NotImplementedException;

import java.net.URI;
import java.net.http.HttpRequest;
import java.util.Collections;
import java.util.Date;

/**
 *
 */
@UtilityClass
public class ExecutorUtils {
    public static Checker createChecker(
            ExecutorApplicationInstanceInfo instanceInfo,
            CheckSpec checkSpec) {
        return checkSpec.getMode().accept(new CheckModeSpecVisitor<>() {
            @Override
            public Checker visit(HTTPCheckModeSpec httpCheck) {
                return new HttpChecker(checkSpec, httpCheck, instanceInfo);
            }

            @Override
            public Checker visit(CmdCheckModeSpec cmdCheck) {
                throw new NotImplementedException("Command check is not yet implemented");
            }
        });
    }

    public static InstanceInfo convert(final StateData<InstanceState, ExecutorApplicationInstanceInfo> state) {
        val data = state.getData();
        return new InstanceInfo(
                data.getAppId(),
                data.getAppName(),
                data.getInstanceId(),
                data.getExecutorId(),
                data.getLocalInfo(),
                data.getResources(),
                state.getState(),
                Collections.emptyMap(),
                state.getError(),
                data.getCreated(),
                new Date());
    }

    public static TaskInstanceInfo convertToTaskInfo(final StateData<TaskInstanceState, ExecutorTaskInstanceInfo> state) {
        val data = state.getData();
        return new TaskInstanceInfo(
                data.getSourceAppName(),
                data.getTaskId(),
                data.getInstanceId(),
                data.getExecutorId(),
                data.getHostname(),
                data.getExecutable(),
                data.getResources(),
                data.getVolumes(),
                data.getLoggingSpec(),
                data.getEnv(),
                state.getState(),
                Collections.emptyMap(),
                state.getError(),
                data.getCreated(),
                new Date());
    }

    public static ExecutorResourceSnapshot executorSnapshot(ResourceInfo resourceState, String executorId) {
        return new ExecutorResourceSnapshot(executorId,
                                            resourceState.getCpu(),
                                            resourceState.getMemory());
    }

    public static HttpRequest.Builder buildRequestFromSpec(final HTTPCheckModeSpec httpSpec, final URI uri) {
        val requestBuilder = HttpRequest.newBuilder(uri);
        return switch (httpSpec.getVerb()) {
            case GET -> requestBuilder.GET();
            case POST -> Strings.isNullOrEmpty(httpSpec.getPayload())
                         ? requestBuilder.POST(HttpRequest.BodyPublishers.noBody())
                         : requestBuilder.POST(HttpRequest.BodyPublishers.ofString(httpSpec.getPayload()));
            case PUT -> Strings.isNullOrEmpty(httpSpec.getPayload())
                        ? requestBuilder.PUT(HttpRequest.BodyPublishers.noBody())
                        : requestBuilder.PUT(HttpRequest.BodyPublishers.ofString(httpSpec.getPayload()));
        };
    }

    public static String instanceId(final DeployedExecutorInstanceInfo instanceInfo) {
        return instanceInfo.accept(new DeployedExecutorInstanceInfoVisitor<>() {
            @Override
            public String visit(ExecutorApplicationInstanceInfo applicationInstanceInfo) {
                return applicationInstanceInfo.getInstanceId();
            }

            @Override
            public String visit(ExecutorTaskInstanceInfo taskInstanceInfo) {
                return taskInstanceInfo.getInstanceId();
            }
        });
    }
}
