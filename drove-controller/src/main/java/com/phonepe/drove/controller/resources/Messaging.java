package com.phonepe.drove.controller.resources;

import com.phonepe.drove.auth.model.DroveUser;
import com.phonepe.drove.auth.model.DroveUserRole;
import com.phonepe.drove.common.model.MessageResponse;
import com.phonepe.drove.common.model.controller.ControllerMessage;
import com.phonepe.drove.controller.engine.ControllerCommunicator;
import io.dropwizard.auth.Auth;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.security.RolesAllowed;
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
@Path("/v1/messages")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed(DroveUserRole.Values.DROVE_CLUSTER_NODE_ROLE)
@Slf4j
public class Messaging {
    private final ControllerCommunicator communicator;

    @Inject
    public Messaging(ControllerCommunicator communicator) {
        this.communicator = communicator;
    }

    @POST
    public MessageResponse receiveCommand(@Auth final DroveUser user, @NotNull@Valid final ControllerMessage message) {
        log.info("Received message of type {} from {}", message.getType(), user.getName());
        return communicator.receive(message);
    }
}
