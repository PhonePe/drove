package com.phonepe.drove.controller.resources;

import com.google.common.base.Strings;
import com.phonepe.drove.auth.model.DroveUser;
import com.phonepe.drove.auth.model.DroveUserRole;
import com.phonepe.drove.auth.model.DroveUserVisitorAdaptor;
import com.phonepe.drove.controller.config.ControllerOptions;
import com.phonepe.drove.controller.statedb.ApplicationInstanceInfoDB;
import com.phonepe.drove.controller.statedb.ApplicationStateDB;
import com.phonepe.drove.controller.statedb.TaskDB;
import com.phonepe.drove.controller.ui.views.*;
import io.dropwizard.auth.Auth;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import ru.vyarus.guicey.gsp.views.template.Template;

import javax.annotation.security.PermitAll;
import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.net.URI;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 *
 */
@Slf4j
@Path("/ui")
@Template
@Produces({MediaType.TEXT_HTML, MediaType.APPLICATION_JSON})
@PermitAll
public class UI {

    private static final Set<DroveUserRole> READ_ALLOWED_ROLES = EnumSet.of(DroveUserRole.EXTERNAL_READ_WRITE,
                                                                            DroveUserRole.EXTERNAL_READ_ONLY);
    private final ApplicationStateDB stateDB;
    private final ApplicationInstanceInfoDB instanceInfoDB;
    private final TaskDB taskDB;

    private final ControllerOptions controllerOptions;


    @Inject
    public UI(
            ApplicationStateDB stateDB,
            ApplicationInstanceInfoDB instanceInfoDB,
            TaskDB taskDB,
            ControllerOptions controllerOptions) {
        this.stateDB = stateDB;
        this.instanceInfoDB = instanceInfoDB;
        this.taskDB = taskDB;
        this.controllerOptions = controllerOptions;
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
            @Auth DroveUser user,
            @PathParam("id") final String appId,
            @PathParam("instanceId") final String instanceId) {
        if (Strings.isNullOrEmpty(appId) || stateDB.application(appId).isEmpty()) {
            throw new WebApplicationException(Response.seeOther(URI.create("/")).build());
        }
        return new InstanceDetailsPage(appId,
                                       instanceId,
                                       instanceInfoDB.instance(appId, instanceId).orElse(null),
                                       hasReadAccess(user));
    }

    @GET
    @Path("/applications/{id}/instances/{instanceId}/stream")
    public LogPailerPage appLogPailerPage(
            @PathParam("id") final String appId,
            @PathParam("instanceId") final String instanceId,
            @QueryParam("logFileName") final String logFileName) {
        if (Strings.isNullOrEmpty(appId) || stateDB.application(appId).isEmpty()) {
            throw new WebApplicationException(Response.seeOther(URI.create("/")).build());
        }
        return new LogPailerPage("applications", appId, instanceId, logFileName);
    }

    @GET
    @Path("/tasks/{sourceAppName}/{taskId}")
    public TaskDetailsPage taskDetailsPage(
            @PathParam("sourceAppName") final String sourceAppName,
            @PathParam("taskId") final String taskId) {
        if (Strings.isNullOrEmpty(sourceAppName) || Strings.isNullOrEmpty(taskId)) {
            throw new WebApplicationException(Response.seeOther(URI.create("/")).build());
        }
        val task = taskDB.task(sourceAppName, taskId).orElse(null);

        return new TaskDetailsPage(sourceAppName, taskId, task);
    }

    @GET
    @Path("/tasks/{id}/instances/{instanceId}/stream")
    public LogPailerPage taskLogPailerPage(
            @PathParam("id") final String appId,
            @PathParam("instanceId") final String instanceId,
            @QueryParam("logFileName") final String logFileName) {
        if (Strings.isNullOrEmpty(appId) || Strings.isNullOrEmpty(instanceId)) {
            throw new WebApplicationException(Response.seeOther(URI.create("/")).build());
        }
        return new LogPailerPage("tasks", appId, instanceId, logFileName);
    }

    @GET
    @Path("/executors/{id}")
    public ExecutorDetailsPageView executorDetails(@PathParam("id") final String executorId) {
        if (Strings.isNullOrEmpty(executorId)) {
            throw new WebApplicationException(Response.seeOther(URI.create("/")).build());
        }
        return new ExecutorDetailsPageView(executorId);
    }


    private boolean hasReadAccess(DroveUser user) {
        return Objects.requireNonNullElse(controllerOptions.getDisableReadAuth(), false)
                || user.accept(new DroveUserVisitorAdaptor<>(READ_ALLOWED_ROLES.contains(user.getRole())) {});
    }
}
