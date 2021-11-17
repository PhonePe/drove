package com.phonepe.drove.controller.resources;

import com.google.common.base.Strings;
import com.phonepe.drove.controller.statedb.ApplicationStateDB;
import com.phonepe.drove.controller.ui.views.*;
import com.phonepe.drove.models.instance.InstanceState;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import ru.vyarus.guicey.gsp.views.template.Template;

import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.net.URI;
import java.util.EnumSet;

/**
 *
 */
@Slf4j
@Path("/ui")
@Template
@Produces({MediaType.TEXT_HTML, MediaType.APPLICATION_JSON})
public class UI {
    private final ResponseEngine responseEngine;
    private final ApplicationStateDB stateDB;

    @Inject
    public UI(ResponseEngine responseEngine, ApplicationStateDB stateDB) {
        this.responseEngine = responseEngine;
        this.stateDB = stateDB;
    }

    @GET
    public HomeView home() {
        return new HomeView();
    }

    @GET
    @Path("/cluster")
    public ClusterView cluster() {
        val cluster = responseEngine.cluster();
        return new ClusterView(cluster.getData());
    }

    @GET
    @Path("/applications")
    public ApplicationsView applications() { //TODO::PAGINATE
        return new ApplicationsView(responseEngine.applications(0, Integer.MAX_VALUE).getData().values());
    }

    @GET
    @Path("/executors")
    public ExecutorsView executors() { //TODO::PAGINATE
        return new ExecutorsView(responseEngine.nodes().getData());
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
    @Path("/applications/{id}/summary")
    public ApplicationSummaryView applicationSummary(@PathParam("id") final String appId) {
        if (Strings.isNullOrEmpty(appId) || stateDB.application(appId).isEmpty()) {
            throw new WebApplicationException(Response.seeOther(URI.create("/")).build());
        }
        return new ApplicationSummaryView(responseEngine.application(appId).getData());
    }

    @GET
    @Path("/applications/{id}/instances")
    public ApplicationInstancesView applicationInstances(@PathParam("id") final String appId) {
        if (Strings.isNullOrEmpty(appId) || stateDB.application(appId).isEmpty()) {
            throw new WebApplicationException(Response.seeOther(URI.create("/")).build());
        }
        //TODO::SORT
        return new ApplicationInstancesView(responseEngine.applicationInstances(appId,
                                                                                EnumSet.allOf(InstanceState.class))
                                                    .getData());
    }
}
