package com.phonepe.drove.executor.resources;

import com.phonepe.drove.common.model.executor.ExecutorMessage;
import com.phonepe.drove.executor.engine.ExecutorCommunicator;
import com.phonepe.drove.common.model.MessageResponse;

import javax.inject.Inject;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

/**
 *
 */
@Path("/messages")
@Produces(MediaType.APPLICATION_JSON)
public class Messaging {
    private final ExecutorCommunicator communicator;

    @Inject
    public Messaging(ExecutorCommunicator communicator) {
        this.communicator = communicator;
    }

    @Path("/v1")
    @POST
    public MessageResponse receiveCommand(@NotNull @Valid final ExecutorMessage message) {
        return communicator.receive(message);
    }
}
