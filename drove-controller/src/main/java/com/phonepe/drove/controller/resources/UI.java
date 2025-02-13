/*
 *  Copyright (c) 2024 Original Author(s), PhonePe India Pvt. Ltd.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

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

import static com.phonepe.drove.auth.model.DroveUserRole.*;

/**
 *
 */
@Slf4j
@Path("/ui")
@Template
@Produces({MediaType.TEXT_HTML, MediaType.APPLICATION_JSON})
@PermitAll
public class UI {

    private static final Set<DroveUserRole> READ_ALLOWED_ROLES = EnumSet.of(EXTERNAL_ROOT,
                                                                            EXTERNAL_READ_WRITE,
                                                                            EXTERNAL_READ_ONLY,
                                                                            DROVE_EXTERNAL_MAINTENANCE);
    private final ApplicationStateDB applicationStateDB;
    private final ApplicationInstanceInfoDB instanceInfoDB;
    private final TaskDB taskDB;
    private final LocalServiceStateDB localServiceStateDB;

    private final ControllerOptions controllerOptions;
    private final InstallationMetadata installationMetadata;


    @Inject
    public UI(
            ApplicationStateDB applicationStateDB,
            ApplicationInstanceInfoDB instanceInfoDB,
            TaskDB taskDB,
            LocalServiceStateDB localServiceStateDB,
            ControllerOptions controllerOptions,
            InstallationMetadata installationMetadata) {
        this.applicationStateDB = applicationStateDB;
        this.instanceInfoDB = instanceInfoDB;
        this.taskDB = taskDB;
        this.localServiceStateDB = localServiceStateDB;
        this.controllerOptions = controllerOptions;
        this.installationMetadata = installationMetadata;
    }

    @GET
    public HomeView home() {
        return new HomeView();
    }

    @GET
    @Path("/applications/{id}")
    public ApplicationDetailsPageView applicationDetails(@PathParam("id") final String appId) {
        if (Strings.isNullOrEmpty(appId) || applicationStateDB.application(appId).isEmpty()) {
            throw new WebApplicationException(Response.seeOther(URI.create("/")).build());
        }
        return new ApplicationDetailsPageView(installationMetadata, appId);
    }


    @GET
    @Path("/applications/{id}/instances/{instanceId}")
    public ApplicationInstanceDetailsPage applicationInstanceDetailsPage(
            @Auth DroveUser user,
            @PathParam("id") final String appId,
            @PathParam("instanceId") final String instanceId) {
        if (Strings.isNullOrEmpty(appId) || applicationStateDB.application(appId).isEmpty()) {
            throw new WebApplicationException(Response.seeOther(URI.create("/")).build());
        }
        return new ApplicationInstanceDetailsPage(
                installationMetadata,
                appId,
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
        if (Strings.isNullOrEmpty(appId) || applicationStateDB.application(appId).isEmpty()) {
            throw new WebApplicationException(Response.seeOther(URI.create("/")).build());
        }
        return new LogPailerPage(installationMetadata, "applications", appId, instanceId, logFileName);
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

        return new TaskDetailsPage(installationMetadata, sourceAppName, taskId, task);
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
        return new LogPailerPage(installationMetadata, "tasks", appId, instanceId, logFileName);
    }

    @GET
    @Path("/localservices/{id}")
    public LocalServiceDetailsPageView localserviceDetails(@PathParam("id") final String serviceId) {
        if (Strings.isNullOrEmpty(serviceId) || localServiceStateDB.service(serviceId).isEmpty()) {
            throw new WebApplicationException(Response.seeOther(URI.create("/")).build());
        }
        return new LocalServiceDetailsPageView(installationMetadata, serviceId);
    }

    @GET
    @Path("/localservices/{id}/instances/{instanceId}")
    public LocalServiceInstanceDetailsPage localServiceInstanceDetailsPage(
            @Auth DroveUser user,
            @PathParam("id") final String serviceId,
            @PathParam("instanceId") final String instanceId) {
        if (Strings.isNullOrEmpty(serviceId) || localServiceStateDB.service(serviceId).isEmpty()) {
            throw new WebApplicationException(Response.seeOther(URI.create("/")).build());
        }
        return new LocalServiceInstanceDetailsPage(installationMetadata,
                                                   serviceId,
                                                   instanceId,
                                                   localServiceStateDB.instance(serviceId, instanceId).orElse(null),
                                                   hasReadAccess(user));
    }

    @GET
    @Path("/localservices/{id}/instances/{instanceId}/stream")
    public LogPailerPage localServiceLogPailerPage(
            @PathParam("id") final String serviceId,
            @PathParam("instanceId") final String instanceId,
            @QueryParam("logFileName") final String logFileName) {
        if (Strings.isNullOrEmpty(serviceId) || localServiceStateDB.service(serviceId).isEmpty()) {
            throw new WebApplicationException(Response.seeOther(URI.create("/")).build());
        }
        return new LogPailerPage(installationMetadata, "localservices", serviceId, instanceId, logFileName);
    }

    @GET
    @Path("/executors/{id}")
    public ExecutorDetailsPageView executorDetails(@PathParam("id") final String executorId) {
        if (Strings.isNullOrEmpty(executorId)) {
            throw new WebApplicationException(Response.seeOther(URI.create("/")).build());
        }
        return new ExecutorDetailsPageView(installationMetadata, executorId);
    }


    private boolean hasReadAccess(DroveUser user) {
        return Objects.requireNonNullElse(controllerOptions.getDisableReadAuth(), false)
                || user.accept(new DroveUserVisitorAdaptor<>(READ_ALLOWED_ROLES.contains(user.getRole())) {});
    }
}
