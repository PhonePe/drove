package com.phonepe.drove.executor.statemachine.actions;

import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.model.PullResponseItem;
import com.google.common.base.Strings;
import com.phonepe.drove.common.StateData;
import com.phonepe.drove.executor.model.ExecutorInstanceInfo;
import com.phonepe.drove.executor.statemachine.InstanceAction;
import com.phonepe.drove.executor.statemachine.InstanceActionContext;
import com.phonepe.drove.models.application.executable.DockerCoordinates;
import com.phonepe.drove.models.instance.InstanceState;
import io.appform.functionmetrics.MonitoredFunction;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 *
 */
@Slf4j
public class ExecutableFetchAction extends InstanceAction {
    @Override
    @MonitoredFunction(method = "execute")
    protected StateData<InstanceState, ExecutorInstanceInfo> executeImpl(
            InstanceActionContext context, StateData<InstanceState, ExecutorInstanceInfo> currentState) {
        val instanceSpec = context.getInstanceSpec();
        val client = context.getClient();
        val image = instanceSpec.getExecutable().accept(DockerCoordinates::getUrl);
        log.info("Pulling docker image: {}", image);
        try {
            client.pullImageCmd(image).exec(new ProgessHandler(image)).awaitCompletion();
            val imageId = client.inspectImageCmd(image).exec().getId();
            log.info("Pulled image {} with image ID: {}", image, imageId);
            context.setDockerImageId(image);
            return StateData.create(InstanceState.STARTING, currentState.getData());
        }
        catch (InterruptedException e) {
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
    public void stop() {
        //Nothing to do here
    }

    private static class ProgessHandler extends ResultCallback.Adapter<PullResponseItem> {

        private final Map<String, Long> downloadState = new ConcurrentHashMap<>();
        private final String image;

        private ProgessHandler(String image) {
            this.image = image;
        }

        @Override
        public void onNext(PullResponseItem responseItem) {
            val layerId = responseItem.getId();
            if (!Strings.isNullOrEmpty(layerId)) {
                val progressDetail = responseItem.getProgressDetail();
                if (null == progressDetail) {
                    log.info("Image:{} Layer {}: {}", image, layerId, responseItem.getStatus());
                }
                else {
                    val downloadPercent = (long)(((double)Objects.requireNonNullElse(progressDetail.getCurrent(), 0L)
                            / Objects.requireNonNullElse(progressDetail.getTotal(),
                                                         1L)) * 100);
                    val oldValue = downloadState.put(layerId, downloadPercent);
                    if (oldValue == null || !oldValue.equals(downloadPercent)) {
                        log.info("Image: {} Layer {}: {}%", image, layerId, downloadPercent);
                    }

                }
            }
        }
    }
}
