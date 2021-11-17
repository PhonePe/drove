package com.phonepe.drove.controller.resources;

import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.phonepe.drove.controller.statedb.ApplicationStateDB;
import com.phonepe.drove.controller.ui.views.*;
import com.phonepe.drove.models.api.AppSummary;
import com.phonepe.drove.models.api.ClusterSummary;
import com.phonepe.drove.models.api.ExecutorSummary;
import com.phonepe.drove.models.instance.InstanceInfo;
import com.phonepe.drove.models.instance.InstanceState;
import lombok.AllArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import ru.vyarus.guicey.gsp.views.template.Template;

import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.net.URI;
import java.util.*;
import java.util.stream.Collectors;

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
    @Path("/cluster/dt")
    public DataTableResponse<ClusterSummary> clusterSummary() {
        val cluster = responseEngine.cluster();
        return new DataTableResponse<>(cluster.getData());
    }

    @GET
    @Path("/applications/dt")
    public DataTableResponse<AppSummary> applicationSummaries() { //TODO::PAGINATE
        return new DataTableResponse<>(responseEngine.applications(0, Integer.MAX_VALUE).getData().values());
    }

    @GET
    @Path("/executors/dt")
    public DataTableResponse<ExecutorSummary> executorSummaries() { //TODO::PAGINATE
        return new DataTableResponse<>(responseEngine.nodes().getData());
    }

    @GET
    @Path("/applications/{id}/summary/dt")
    public DataTableResponse<AppSummary> applicationSummaryDT(@PathParam("id") final String appId) {
        if (Strings.isNullOrEmpty(appId) || stateDB.application(appId).isEmpty()) {
            throw new WebApplicationException(Response.seeOther(URI.create("/")).build());
        }
        return new DataTableResponse<>(responseEngine.application(appId).getData());
    }

    @GET
    @Path("/applications/{id}/instances/dt")
    public DataTableResponse<Map<String, Object>> applicationInstancesDT(@PathParam("id") final String appId) {
        if (Strings.isNullOrEmpty(appId) || stateDB.application(appId).isEmpty()) {
            throw new WebApplicationException(Response.seeOther(URI.create("/")).build());
        }
        //TODO::SORT
        val instances = responseEngine.applicationInstances(appId,
                                                            EnumSet.allOf(InstanceState.class))
                .getData();
        if (null != instances) {
            return new DataTableResponse<>(instances.stream().map(UI::toMap).collect(Collectors.toUnmodifiableList()));
        }
        return new DataTableResponse<>(Collections.emptyList());
    }

    private static Map<String, Object> toMap(final InstanceInfo instanceInfo) {
        return Map.of("instanceId", instanceInfo.getInstanceId(),
                      "executorId", instanceInfo.getExecutorId(),
                      "hostname", null != instanceInfo.getLocalInfo()
                                        ? instanceInfo.getLocalInfo().getHostname()
                                        : "",
                      "ports",
                      null != instanceInfo.getLocalInfo()
                      ? Joiner.on(",")
                              .join(instanceInfo.getLocalInfo()
                                            .getPorts()
                                            .entrySet()
                                            .stream()
                                            .map(e -> e.getKey() + ": " + e.getValue().getHostPort())
                                            .collect(Collectors.toUnmodifiableList()))
                      : "",
                      "state", instanceInfo.getState(),
                      "logStream", "TODO");
    }
}
