package com.phonepe.drove.executor.managed;

import com.phonepe.drove.common.model.MessageDeliveryStatus;
import com.phonepe.drove.common.model.MessageHeader;
import com.phonepe.drove.common.model.controller.InstanceStateReportMessage;
import com.phonepe.drove.executor.Utils;
import com.phonepe.drove.executor.engine.ExecutorCommunicator;
import com.phonepe.drove.executor.engine.InstanceEngine;
import com.phonepe.drove.executor.resourcemgmt.ResourceDB;
import com.phonepe.drove.models.instance.InstanceInfo;
import io.dropwizard.lifecycle.Managed;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import ru.vyarus.dropwizard.guice.module.installer.order.Order;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 *
 */
@Slf4j
@Singleton
@Order(50)
public class ExecutorInstanceStateChangeNotifier implements Managed {
    private static final String STATE_CHANGE_HANDLER_NAME = "state-change-notifier";
    private final ResourceDB resourceDB;
    private final ExecutorCommunicator communicator;
    private final InstanceEngine engine;

    @Inject
    public ExecutorInstanceStateChangeNotifier(
            ResourceDB resourceDB, ExecutorCommunicator communicator, InstanceEngine engine) {
        this.resourceDB = resourceDB;
        this.communicator = communicator;
        this.engine = engine;
    }

    private void handleStateChange(final InstanceInfo instanceInfo) {
        log.debug("Received state change notification: {}", instanceInfo);
        val executorId = instanceInfo.getExecutorId();
        val snapshot = Utils.executorSnapshot(resourceDB.currentState(), executorId);
        val resp = communicator.send(new InstanceStateReportMessage(MessageHeader.executorRequest(),
                                                                    snapshot,
                                                                    instanceInfo)).getStatus();
        if (!resp.equals(MessageDeliveryStatus.ACCEPTED)) {
            log.info("Sending message to controller failed with status: {}.", resp);
        }
    }

    @Override
    public void start() throws Exception {
        engine.onStateChange().connect(STATE_CHANGE_HANDLER_NAME, this::handleStateChange);
        log.info("State updater started");
    }

    @Override
    public void stop() throws Exception {
        engine.onStateChange().disconnect(STATE_CHANGE_HANDLER_NAME);
        log.info("State updater stopped");
    }
}
