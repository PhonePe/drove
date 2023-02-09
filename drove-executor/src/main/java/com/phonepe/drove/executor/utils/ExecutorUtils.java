package com.phonepe.drove.executor.utils;

import com.google.common.base.Strings;
import com.phonepe.drove.common.model.ApplicationInstanceSpec;
import com.phonepe.drove.executor.checker.Checker;
import com.phonepe.drove.executor.checker.CmdChecker;
import com.phonepe.drove.executor.checker.HttpChecker;
import com.phonepe.drove.executor.model.DeployedExecutionObjectInfo;
import com.phonepe.drove.executor.model.DeployedExecutorInstanceInfoVisitor;
import com.phonepe.drove.executor.model.ExecutorInstanceInfo;
import com.phonepe.drove.executor.model.ExecutorTaskInfo;
import com.phonepe.drove.executor.resourcemgmt.ResourceInfo;
import com.phonepe.drove.executor.statemachine.InstanceActionContext;
import com.phonepe.drove.models.application.checks.CheckModeSpecVisitor;
import com.phonepe.drove.models.application.checks.CheckSpec;
import com.phonepe.drove.models.application.checks.CmdCheckModeSpec;
import com.phonepe.drove.models.application.checks.HTTPCheckModeSpec;
import com.phonepe.drove.models.info.ExecutorResourceSnapshot;
import com.phonepe.drove.models.instance.InstanceInfo;
import com.phonepe.drove.models.instance.InstanceState;
import com.phonepe.drove.models.taskinstance.TaskInfo;
import com.phonepe.drove.models.taskinstance.TaskResult;
import com.phonepe.drove.models.taskinstance.TaskState;
import com.phonepe.drove.statemachine.StateData;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import lombok.val;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.classic.methods.HttpPut;
import org.apache.hc.client5.http.classic.methods.HttpUriRequest;
import org.apache.hc.core5.http.io.entity.StringEntity;

import java.net.URI;
import java.util.Date;

/**
 *
 */
@UtilityClass
public class ExecutorUtils {
    public static Checker createChecker(
            InstanceActionContext<ApplicationInstanceSpec> context, ExecutorInstanceInfo instanceInfo,
            CheckSpec checkSpec) {
        return checkSpec.getMode().accept(new CheckModeSpecVisitor<>() {
            @Override
            public Checker visit(HTTPCheckModeSpec httpCheck) {
                return new HttpChecker(checkSpec, httpCheck, instanceInfo);
            }

            @Override
            public Checker visit(CmdCheckModeSpec cmdCheck) {
                return new CmdChecker(cmdCheck, context);
            }
        });
    }

    public static InstanceInfo convert(final StateData<InstanceState, ExecutorInstanceInfo> state) {
        val data = state.getData();
        return new InstanceInfo(
                data.getAppId(),
                data.getAppName(),
                data.getInstanceId(),
                data.getExecutorId(),
                data.getLocalInfo(),
                data.getResources(),
                state.getState(),
                data.getMetadata(),
                state.getError(),
                data.getCreated(),
                new Date());
    }

    public static TaskInfo convertToTaskInfo(final StateData<TaskState, ExecutorTaskInfo> state) {
        val data = state.getData();
        return new TaskInfo(
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
                data.getMetadata(),
                data.getTaskResult(),
                state.getError(),
                data.getCreated(),
                new Date());
    }

    public static ExecutorResourceSnapshot executorSnapshot(ResourceInfo resourceState, String executorId) {
        return new ExecutorResourceSnapshot(executorId,
                                            resourceState.getCpu(),
                                            resourceState.getMemory());
    }

    @SneakyThrows
    public static HttpUriRequest buildRequestFromSpec(final HTTPCheckModeSpec httpSpec, final URI uri) {
        return switch (httpSpec.getVerb()) {
            case GET -> new HttpGet(uri);
            case POST -> {
                val req = new HttpPost(uri);
                if(!Strings.isNullOrEmpty(httpSpec.getPayload())) {
                    req.setEntity(new StringEntity(httpSpec.getPayload()));
                }
                yield req;
            }
            case PUT -> {
                val req = new HttpPut(uri);
                if(!Strings.isNullOrEmpty(httpSpec.getPayload())) {
                    req.setEntity(new StringEntity(httpSpec.getPayload()));
                }
                yield req;
            }
        };
    }

    public static String instanceId(final DeployedExecutionObjectInfo instanceInfo) {
        return instanceInfo.accept(new DeployedExecutorInstanceInfoVisitor<>() {
            @Override
            public String visit(ExecutorInstanceInfo applicationInstanceInfo) {
                return applicationInstanceInfo.getInstanceId();
            }

            @Override
            public String visit(ExecutorTaskInfo taskInfo) {
                return taskInfo.getInstanceId();
            }
        });
    }

    public static StateData<TaskState, ExecutorTaskInfo> injectResult(
            final StateData<TaskState, ExecutorTaskInfo> currState,
            final TaskResult result) {
        val curr = currState.getData();
        return StateData.create(currState.getState(),
                                new ExecutorTaskInfo(
                                        curr.getTaskId(),
                                        curr.getSourceAppName(),
                                        curr.getInstanceId(),
                                        curr.getExecutorId(),
                                        curr.getHostname(),
                                        curr.getExecutable(),
                                        curr.getResources(),
                                        curr.getVolumes(),
                                        curr.getLoggingSpec(),
                                        curr.getEnv(),
                                        curr.getMetadata(),
                                        result,
                                        curr.getCreated(),
                                        new Date()),
                currState.getError());
    }
}
