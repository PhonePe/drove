package com.phonepe.drove.controller.resources;

import com.phonepe.drove.controller.ui.views.ApplicationsView;
import com.phonepe.drove.controller.ui.views.ClusterView;
import com.phonepe.drove.controller.ui.views.ExecutorsView;
import com.phonepe.drove.controller.ui.views.HomeView;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import ru.vyarus.guicey.gsp.views.template.Template;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

/**
 *
 */
@Slf4j
@Path("/ui")
@Template
@Produces({MediaType.TEXT_HTML, MediaType.APPLICATION_JSON})
public class UI {
    private final ResponseEngine responseEngine;

    @Inject
    public UI(ResponseEngine responseEngine) {
        this.responseEngine = responseEngine;
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
}
