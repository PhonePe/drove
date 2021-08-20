package com.phonepe.drove.common;

import com.phonepe.drove.internalmodels.Message;
import com.phonepe.drove.internalmodels.MessageResponse;
import io.appform.signals.signals.ConsumingSyncSignal;

/**
 *
 */
public interface Communicator {
    ConsumingSyncSignal<MessageResponse> onResponse();

    void send(final Message message);
}
