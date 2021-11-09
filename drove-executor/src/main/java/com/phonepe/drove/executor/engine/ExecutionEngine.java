package com.phonepe.drove.executor.engine;

import com.phonepe.drove.common.model.InstanceSpec;
import com.phonepe.drove.common.model.StopInstanceMessage2;
import com.phonepe.drove.common.net.Communicator;

/**
 *
 */
public interface ExecutionEngine {
    void startContainer(final InstanceSpec startInstanceMessage, final Communicator communicator);
    void stopContainer(final StopInstanceMessage2 startInstanceMessage, final Communicator communicator);
    void getContainerInfo(final StopInstanceMessage2 startInstanceMessage, final Communicator communicator);
}
