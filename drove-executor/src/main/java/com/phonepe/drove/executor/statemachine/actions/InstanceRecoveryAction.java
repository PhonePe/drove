package com.phonepe.drove.executor.statemachine.actions;

import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.model.Frame;
import com.phonepe.drove.common.StateData;
import com.phonepe.drove.executor.engine.DockerLabels;
import com.phonepe.drove.executor.statemachine.InstanceAction;
import com.phonepe.drove.executor.statemachine.InstanceActionContext;
import com.phonepe.drove.models.instance.InstanceInfo;
import com.phonepe.drove.models.instance.InstanceState;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import java.nio.charset.Charset;
import java.util.Collections;

/**
 *
 */
@Slf4j
public class InstanceRecoveryAction extends InstanceAction {
    @Override
    public void stop() {

    }

    @Override
    protected StateData<InstanceState, InstanceInfo> executeImpl(
            InstanceActionContext context, StateData<InstanceState, InstanceInfo> currentState) {
        val client = context.getClient();
        val instanceId = currentState.getData().getInstanceId();
        val container = client.listContainersCmd()
                .withLabelFilter(Collections.singletonMap(DockerLabels.DROVE_INSTANCE_ID_LABEL, instanceId))
                .exec()
                .stream()
                .findAny()
                .orElse(null);
        if (null == container) {
            return StateData.errorFrom(currentState,
                                       InstanceState.STOPPED,
                                       "No container found with drove id: " + instanceId);
        }
        val containerId = container.getId();
        context.setDockerInstanceId(containerId);
        context.setDockerImageId(container.getImageId());
        client.logContainerCmd(containerId)
                .withTailAll()
                .withFollowStream(true)
                .withStdOut(true)
                .withStdErr(true)
                .exec(new ResultCallback.Adapter<Frame>() {

                    @Override
                    public void onNext(Frame object) {
                        switch (object.getStreamType())  {
                            case STDOUT:
                                log.info(new String(object.getPayload(), Charset.defaultCharset()));
                                break;
                            case STDERR:
                                log.error(new String(object.getPayload(), Charset.defaultCharset()));
                                break;
                            case STDIN:
                            case RAW:
                            default:
                                break;
                        }
                    }
                });

        return StateData.from(currentState, InstanceState.UNREADY);
    }
}
