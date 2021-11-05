package com.phonepe.drove.controller.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.phonepe.drove.common.model.ExecutorMessageType;
import com.phonepe.drove.common.model.executor.ExecutorMessage;
import com.phonepe.drove.common.net.RemoteHost;
import com.phonepe.drove.common.net.RemoteMessageSender;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Optional;

/**
 *
 */
@Slf4j
@Singleton
public class RemoteExecutorMessageSender extends RemoteMessageSender<ExecutorMessageType, ExecutorMessage> {

    @Inject
    public RemoteExecutorMessageSender(ObjectMapper mapper) {
        super(mapper);
    }

    @Override
    protected Optional<RemoteHost> translateRemoteAddress(ExecutorMessage message) {
        val host = message.getAddress();
        return Optional.of(new RemoteHost(host.getHostname(), host.getPort()));
    }
}
