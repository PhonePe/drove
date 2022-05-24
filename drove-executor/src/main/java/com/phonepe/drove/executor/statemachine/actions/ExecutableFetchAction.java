package com.phonepe.drove.executor.statemachine.actions;

import com.github.dockerjava.api.command.PullImageCmd;
import com.github.dockerjava.api.model.AuthConfig;
import com.phonepe.drove.executor.dockerauth.DockerAuthConfig;
import com.phonepe.drove.executor.dockerauth.DockerAuthConfigVisitor;
import com.phonepe.drove.executor.model.ExecutorInstanceInfo;
import com.phonepe.drove.executor.statemachine.InstanceAction;
import com.phonepe.drove.executor.statemachine.InstanceActionContext;
import com.phonepe.drove.models.application.executable.DockerCoordinates;
import com.phonepe.drove.models.instance.InstanceState;
import io.appform.functionmetrics.MonitoredFunction;
import io.appform.simplefsm.StateData;
import io.dropwizard.util.Strings;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import javax.inject.Inject;
import java.util.Objects;

/**
 *
 */
@Slf4j
public class ExecutableFetchAction extends InstanceAction {

    private final DockerAuthConfig dockerAuthConfig;

    @Inject
    public ExecutableFetchAction(DockerAuthConfig dockerAuthConfig) {
        this.dockerAuthConfig = dockerAuthConfig;
    }

    @Override
    @MonitoredFunction(method = "execute")
    protected StateData<InstanceState, ExecutorInstanceInfo> executeImpl(
            InstanceActionContext context, StateData<InstanceState, ExecutorInstanceInfo> currentState) {
        val instanceSpec = context.getInstanceSpec();
        val client = context.getClient();
        val image = instanceSpec.getExecutable().accept(DockerCoordinates::getUrl);
        log.info("Pulling docker image: {}", image);
        try {
            val pullImageCmd = client.pullImageCmd(image);
            addAuth(pullImageCmd);
            pullImageCmd.exec(new ImagePullProgressHandler(image)).awaitCompletion();
            val imageId = client.inspectImageCmd(image).exec().getId();
            log.info("Pulled image {} with image ID: {}", image, imageId);
            context.setDockerImageId(imageId);
            return StateData.from(currentState, InstanceState.STARTING);
        }
        catch (InterruptedException e) {
            log.info("Action has been interrupted");
            Thread.currentThread().interrupt();
            return StateData.errorFrom(currentState,
                                       InstanceState.PROVISIONING_FAILED,
                                       "Pull operation interrupted");
        }
        catch (Exception e) {
            return StateData.errorFrom(currentState,
                                       InstanceState.PROVISIONING_FAILED,
                                       "Error while pulling image " + image + ": " + e.getMessage());
        }
    }

    @Override
    protected InstanceState defaultErrorState() {
        return InstanceState.PROVISIONING_FAILED;
    }

    @Override
    public void stop() {
        //Nothing to do here
    }

    @SneakyThrows
    private void addAuth(PullImageCmd pullImageCmd) {
        if(null == dockerAuthConfig) {
            log.debug("No docker auth specified");
            return;
        }
        val authEntries = dockerAuthConfig.getEntries();
        val imageUri = Objects.requireNonNullElse(pullImageCmd.getRepository(), "");
        val registry = imageUri.substring(0, imageUri.indexOf("/"));
        if(Strings.isNullOrEmpty(registry)) {
            return;
        }
        log.info("Docker image registry for auth lookup: {}", registry);
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
