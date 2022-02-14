package com.phonepe.drove.executor.resources;

import com.codahale.metrics.annotation.Timed;
import com.phonepe.drove.common.auth.model.DroveUser;
import com.phonepe.drove.common.auth.model.DroveUserRole;
import com.phonepe.drove.common.model.MessageResponse;
import com.phonepe.drove.common.model.executor.ExecutorMessage;
import com.phonepe.drove.executor.engine.ExecutorCommunicator;
import io.dropwizard.auth.Auth;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.security.RolesAllowed;
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
@Path("/v1/messages")
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed(DroveUserRole.Values.DROVE_CLUSTER_NODE_ROLE)
@Slf4j
public class Messaging {
    private final ExecutorCommunicator communicator;

    @Inject
    public Messaging(ExecutorCommunicator communicator) {
        this.communicator = communicator;
    }

    @POST
    @Timed
    public MessageResponse receiveCommand(@Auth final DroveUser user, @NotNull@Valid final ExecutorMessage message) {
        log.info("Received message of type {} from {}", message.getType(), user.getName());
        return communicator.receive(message);
    }
}
