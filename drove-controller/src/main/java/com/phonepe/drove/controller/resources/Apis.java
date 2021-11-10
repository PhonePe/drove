package com.phonepe.drove.controller.resources;

import com.phonepe.drove.controller.engine.ApplicationEngine;
import com.phonepe.drove.controller.utils.ControllerUtils;
import com.phonepe.drove.models.operation.ApplicationOperation;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.Collections;

/**
 *
 */
@Path("/operations/v1")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Singleton
@Slf4j
public class Apis {
    private final ApplicationEngine engine;

    @Inject
    public Apis(ApplicationEngine engine) {
        this.engine = engine;
    }

    @POST
    public Response acceptOperation(@NotNull @Valid final ApplicationOperation operation) {
        engine.handleOperation(operation);
        return Response.ok(Collections.singletonMap("appId", ControllerUtils.appId(operation))).build();
    }
}
