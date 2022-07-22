package com.phonepe.drove.controller.resources;

import com.google.common.base.Strings;
import com.phonepe.drove.controller.statedb.ApplicationStateDB;
import com.phonepe.drove.controller.statedb.ApplicationInstanceInfoDB;
import com.phonepe.drove.controller.ui.views.*;
import lombok.extern.slf4j.Slf4j;
import ru.vyarus.guicey.gsp.views.template.Template;

import javax.annotation.security.PermitAll;
import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.net.URI;

/**
 *
 */
@Slf4j
@Path("/ui")
@Template
@Produces({MediaType.TEXT_HTML, MediaType.APPLICATION_JSON})
@PermitAll
public class UI {

    private final ApplicationStateDB stateDB;
    private final ApplicationInstanceInfoDB instanceInfoDB;

    @Inject
    public UI(
            ApplicationStateDB stateDB,
            ApplicationInstanceInfoDB instanceInfoDB) {
        this.stateDB = stateDB;
        this.instanceInfoDB = instanceInfoDB;
    }

    @GET
    public HomeView home() {
        return new HomeView();
    }

    @GET
    @Path("/applications/{id}")
    public ApplicationDetailsPageView applicationDetails(@PathParam("id") final String appId) {
        if (Strings.isNullOrEmpty(appId) || stateDB.application(appId).isEmpty()) {
            throw new WebApplicationException(Response.seeOther(URI.create("/")).build());
        }
        return new ApplicationDetailsPageView(appId);
    }


    @GET
    @Path("/applications/{id}/instances/{instanceId}")
    public InstanceDetailsPage instanceDetailsPage(
            @PathParam("id") final String appId,
            @PathParam("instanceId") final String instanceId) {
        if (Strings.isNullOrEmpty(appId) || stateDB.application(appId).isEmpty()) {
            throw new WebApplicationException(Response.seeOther(URI.create("/")).build());
        }
        return new InstanceDetailsPage(appId, instanceId, instanceInfoDB.instance(appId, instanceId).orElse(null));
    }

    @GET
    @Path("/applications/{id}/instances/{instanceId}/stream")
    public LogPailerPage pailerPage(
            @PathParam("id") final String appId,
            @PathParam("instanceId") final String instanceId,
            @QueryParam("logFileName") final String logFileName) {
        if (Strings.isNullOrEmpty(appId) || stateDB.application(appId).isEmpty()) {
            throw new WebApplicationException(Response.seeOther(URI.create("/")).build());
        }
        return new LogPailerPage(appId, instanceId, logFileName);
    }

    @GET
    @Path("/executors/{id}")
    public ExecutorDetailsPageView executorDetails(@PathParam("id") final String executorId) {
        if (Strings.isNullOrEmpty(executorId)) {
            throw new WebApplicationException(Response.seeOther(URI.create("/")).build());
        }
        return new ExecutorDetailsPageView(executorId);
    }
}
