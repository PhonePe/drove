package com.phonepe.drove.executor;

import com.phonepe.drove.common.Communicator;
import com.phonepe.drove.internalmodels.InstanceSpec;
import com.phonepe.drove.internalmodels.StopInstanceMessage;

/**
 *
 */
public interface ExecutionEngine {
    void startContainer(final InstanceSpec startInstanceMessage, final Communicator communicator);
    void stopContainer(final StopInstanceMessage startInstanceMessage, final Communicator communicator);
    void getContainerInfo(final StopInstanceMessage startInstanceMessage, final Communicator communicator);
}
