package com.phonepe.drove.executor.utils;

import com.google.common.base.Strings;
import com.phonepe.drove.common.StateData;
import com.phonepe.drove.executor.checker.Checker;
import com.phonepe.drove.executor.checker.HttpChecker;
import com.phonepe.drove.executor.model.ExecutorInstanceInfo;
import com.phonepe.drove.executor.resourcemgmt.ResourceInfo;
import com.phonepe.drove.executor.statemachine.InstanceActionContext;
import com.phonepe.drove.models.application.checks.CheckModeSpecVisitor;
import com.phonepe.drove.models.application.checks.CheckSpec;
import com.phonepe.drove.models.application.checks.CmdCheckModeSpec;
import com.phonepe.drove.models.application.checks.HTTPCheckModeSpec;
import com.phonepe.drove.models.info.ExecutorResourceSnapshot;
import com.phonepe.drove.models.instance.InstanceInfo;
import com.phonepe.drove.models.instance.InstanceState;
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
            InstanceActionContext context,
            ExecutorInstanceInfo instanceInfo,
            CheckSpec readinessCheckSpec) {
        return context.getInstanceSpec().getReadiness().getMode().accept(new CheckModeSpecVisitor<>() {
            @Override
            public Checker visit(HTTPCheckModeSpec httpCheck) {
                return new HttpChecker(readinessCheckSpec, httpCheck, instanceInfo);
            }

            @Override
            public Checker visit(CmdCheckModeSpec cmdCheck) {
                throw new NotImplementedException("Command check is not yet implemented");
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
                Collections.emptyMap(),
                state.getError(),
                new Date(),
                new Date());
    }

    public static ExecutorResourceSnapshot executorSnapshot(ResourceInfo resourceState, String executorId) {
        return new ExecutorResourceSnapshot(executorId,
                                            resourceState.getCpu(),
                                            resourceState.getMemory());
    }

    public static HttpRequest.Builder buildRequestFromSpec(final HTTPCheckModeSpec httpSpec, final URI uri) {
        val requestBuilder = HttpRequest.newBuilder(uri);
        switch (httpSpec.getVerb()) {

            case GET: {
                requestBuilder.GET();
                break;
            }
            case POST: {
                if (!Strings.isNullOrEmpty(httpSpec.getPayload())) {
                    requestBuilder.POST(HttpRequest.BodyPublishers.ofString(httpSpec.getPayload()));
                }
                else {
                    requestBuilder.POST(HttpRequest.BodyPublishers.noBody());
                }
                break;
            }
            case PUT: {
                if (!Strings.isNullOrEmpty(httpSpec.getPayload())) {
                    requestBuilder.PUT(HttpRequest.BodyPublishers.ofString(httpSpec.getPayload()));
                }
                else {
                    requestBuilder.PUT(HttpRequest.BodyPublishers.noBody());
                }
                break;
            }
        }
        return requestBuilder;
    }
}
