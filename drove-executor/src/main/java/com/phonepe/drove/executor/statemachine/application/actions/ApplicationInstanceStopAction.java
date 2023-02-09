package com.phonepe.drove.executor.statemachine.application.actions;

import com.github.dockerjava.api.exception.NotFoundException;
import com.google.common.base.Strings;
import com.phonepe.drove.common.CommonUtils;
import com.phonepe.drove.common.model.ApplicationInstanceSpec;
import com.phonepe.drove.executor.model.ExecutorInstanceInfo;
import com.phonepe.drove.executor.statemachine.InstanceActionContext;
import com.phonepe.drove.executor.statemachine.application.ApplicationInstanceAction;
import com.phonepe.drove.executor.utils.ExecutorUtils;
import com.phonepe.drove.models.application.PreShutdownSpec;
import com.phonepe.drove.models.application.checks.CheckMode;
import com.phonepe.drove.models.application.checks.CheckModeSpec;
import com.phonepe.drove.models.application.checks.HTTPCheckModeSpec;
import com.phonepe.drove.models.instance.InstanceState;
import com.phonepe.drove.statemachine.StateData;
import io.appform.functionmetrics.MonitoredFunction;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import net.jodah.failsafe.TimeoutExceededException;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.io.entity.EntityUtils;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 *
 */
@Slf4j
public class ApplicationInstanceStopAction extends ApplicationInstanceAction {
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    @Override
    @MonitoredFunction(method = "execute")
    protected StateData<InstanceState, ExecutorInstanceInfo> executeImpl(
            InstanceActionContext<ApplicationInstanceSpec> context,
            StateData<InstanceState, ExecutorInstanceInfo> currentState) {
        if (Strings.isNullOrEmpty(context.getDockerInstanceId())) {
            log.warn("No docker id found for instance {}. Nothing to be done for stop.",
                     context.getInstanceSpec().getInstanceId());
        }
        else {
            handlePreShutdown(context, currentState);
            val dockerClient = context.getClient();
            try {
                dockerClient.stopContainerCmd(context.getDockerInstanceId()).exec();
            }
            catch (NotFoundException e) {
                log.error("Container already exited");
                return StateData.errorFrom(currentState, InstanceState.DEPROVISIONING, e.getMessage());
            }
            catch (Exception e) {
                log.error("Error stopping instance: " + context.getDockerInstanceId(), e);
                return StateData.errorFrom(currentState, InstanceState.DEPROVISIONING, e.getMessage());
            }
        }
        return StateData.from(currentState, InstanceState.DEPROVISIONING);
    }

    @Override
    public void stop() {
        //Ignored
    }


    @Override
    protected boolean isStopAllowed() {
        return false;
    }

    @Override
    protected InstanceState defaultErrorState() {
        return InstanceState.DEPROVISIONING;
    }

    private void handlePreShutdown(
            final InstanceActionContext<ApplicationInstanceSpec> context,
            final StateData<InstanceState, ExecutorInstanceInfo> currentState) {
        val instanceSpec = context.getInstanceSpec();
        val preShutdown = Objects.requireNonNullElse(instanceSpec.getPreShutdown(), PreShutdownSpec.DEFAULT);
        val preShutdownHook = Objects.requireNonNullElse(preShutdown.getHooks(),
                                                         Collections.<CheckModeSpec>emptyList());
        if (preShutdownHook.isEmpty()) {
            log.info("No pre-shutdown hook configured");
        }
        else {
            log.info("Calling {} pre-shutdown hooks", preShutdownHook.size());
            preShutdownHook.forEach(hook -> executeHook(currentState, hook));
        }
        sleepBeforeKill(preShutdown);
    }

    private void executeHook(
            StateData<InstanceState, ExecutorInstanceInfo> currentState,
            CheckModeSpec preShutdownHook) {
        val httpSpec = CheckMode.HTTP == preShutdownHook.getType()
                       ? (HTTPCheckModeSpec) preShutdownHook
                       : null;
        if (null == httpSpec) {
            log.error("Non-http hooks are not supported yet");
            return;
        }
        val instanceInfo = currentState.getData();
        val port = instanceInfo.getLocalInfo()
                .getPorts()
                .get(httpSpec.getPortName());
        if (null == port) {
            log.error("Shutdown hook will not be executed. No port found with name {}",
                      httpSpec.getPortName());
            return;
        }
        val uri = URI.create(String.format("%s://localhost:%d%s",
                                           httpSpec.getProtocol().name().toLowerCase(),
                                           port.getHostPort(),
                                           httpSpec.getPath()));

        val retryPolicy = new RetryPolicy<Boolean>()
                .withDelay(Duration.ofSeconds(3))
                .withMaxAttempts(5)
                .withMaxDuration(Duration.ofSeconds(30))
                .handle(Exception.class)
                .handleResultIf(r -> !r);
        try (val httpClient = CommonUtils.createInternalHttpClient(httpSpec, Duration.ofSeconds(1))) {
            Failsafe.with(List.of(retryPolicy))
                    .onFailure(e -> log.error("Pre-shutdown hook call failure: ", e.getFailure()))
                    .get(() -> makeHTTPCall(httpClient, httpSpec, uri));
        }
        catch (TimeoutExceededException e) {
            log.error("Timeout calling shutdown hook.");
        }
        catch (IOException e) {
            log.error("Error creating http client: " + e.getMessage());
        }
    }

    private boolean makeHTTPCall(
            final CloseableHttpClient httpClient,
            final HTTPCheckModeSpec httpSpec,
            final URI uri) {
        val request = ExecutorUtils.buildRequestFromSpec(httpSpec, uri);

        try {
            log.info("Calling pre-shutdown hook: {}", uri);
            return httpClient.execute(request, response -> {
                val statusCode = response.getCode();
                val responseBody = null != response.getEntity()
                                   ? EntityUtils.toString(response.getEntity())
                                   : "";
                if (httpSpec.getSuccessCodes().contains(statusCode)) {
                    return true;
                }
                log.error("Pre-shutdown hook failed. Status code: {} response: {}", statusCode, responseBody);
                return false;
            });

        }
        catch (ConnectException e) {
            log.error("Unable to connect to instance");
        }
        catch (IOException e) {
            log.error("Pre-shutdown hook failed. Error Message: " + e.getMessage(), e);
        }
        return false;
    }

    private void sleepBeforeKill(PreShutdownSpec preShutdown) {
        val sleepMillis = Objects.requireNonNullElse(preShutdown.getWaitBeforeKill(),
                                                     io.dropwizard.util.Duration.seconds(0)).toMilliseconds();
        if (sleepMillis == 0) {
            log.warn("No sleep specified. This is not a good practise");
            return;
        }
        log.info("Waiting {} ms before killing container", sleepMillis);
        try {
            Thread.sleep(sleepMillis);
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.info("Sleep before kill interrupted");
        }
    }
}
