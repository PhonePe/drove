package com.phonepe.drove.controller.resources;

import com.google.common.base.Strings;
import com.phonepe.drove.controller.statedb.ApplicationStateDB;
import com.phonepe.drove.controller.ui.views.ApplicationDetailsPageView;
import com.phonepe.drove.controller.ui.views.ExecutorDetailsPageView;
import com.phonepe.drove.controller.ui.views.HomeView;
import lombok.AllArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import ru.vyarus.guicey.gsp.views.template.Template;

import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.net.URI;
import java.util.Collection;
import java.util.Collections;

/**
 *
 */
@Slf4j
@Path("/ui")
@Template
@Produces({MediaType.TEXT_HTML, MediaType.APPLICATION_JSON})
public class UI {
    @Value
    @AllArgsConstructor
    private static class DataTableResponse<T> {
        Collection<T> data;

        public DataTableResponse(T param) {
            this(Collections.singletonList(param));
        }
    }

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
    @Path("/applications/{id}")
    public ApplicationDetailsPageView applicationDetails(@PathParam("id") final String appId) {
        if (Strings.isNullOrEmpty(appId) || stateDB.application(appId).isEmpty()) {
            throw new WebApplicationException(Response.seeOther(URI.create("/")).build());
        }
        return new ApplicationDetailsPageView(appId);
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
