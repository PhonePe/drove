package com.phonepe.drove.controller.httpresources;

import com.phonepe.drove.common.model.MessageResponse;
import com.phonepe.drove.common.model.controller.ControllerMessage;
import com.phonepe.drove.controller.engine.ControllerCommunicator;

import javax.inject.Inject;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

/**
 *
 */
@Path("/messages")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class Messaging {
    private final ControllerCommunicator communicator;

    @Inject
    public Messaging(ControllerCommunicator communicator) {
        this.communicator = communicator;
    }

    @Path("/v1")
    @POST
    public MessageResponse receiveCommand(@NotNull @Valid final ControllerMessage message) {
        return communicator.receive(message);
    }
}
