package com.phonepe.drove.executor.managed;

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
    private final ResourceDB resourceDB;
    private final ExecutorCommunicator communicator;

    @Inject
    public ExecutorInstanceStateChangeNotifier(
            ResourceDB resourceDB, ExecutorCommunicator communicator, InstanceEngine engine) {
        this.resourceDB = resourceDB;
        this.communicator = communicator;
        engine.onStateChange().connect(this::handleStateChange);
    }

    public void handleStateChange(final InstanceInfo instanceInfo) {
        log.info("received state change notification: {}", instanceInfo);
        val executorId = instanceInfo.getExecutorId();
        val snapshot = Utils.executorSnapshot(resourceDB.currentState(), executorId);
        communicator.send(new InstanceStateReportMessage(MessageHeader.executorRequest(), snapshot, instanceInfo));
    }

    @Override
    public void start() throws Exception {
        log.info("State updater started");
    }

    @Override
    public void stop() throws Exception {
        log.info("State updater stopped");
    }
}
