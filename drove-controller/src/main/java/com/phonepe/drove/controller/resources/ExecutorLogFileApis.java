package com.phonepe.drove.controller.resources;

import com.codahale.metrics.annotation.Metered;
import com.phonepe.drove.auth.config.ClusterAuthenticationConfig;
import com.phonepe.drove.auth.model.ClusterCommHeaders;
import com.phonepe.drove.auth.model.DroveUserRole;
import com.phonepe.drove.common.CommonUtils;
import com.phonepe.drove.controller.resourcemgmt.ClusterResourcesDB;
import com.phonepe.drove.controller.resourcemgmt.ExecutorHostInfo;
import com.phonepe.drove.controller.statedb.ApplicationInstanceInfoDB;
import com.phonepe.drove.controller.statedb.TaskDB;
import com.phonepe.drove.models.info.nodedata.ExecutorNodeData;
import com.phonepe.drove.models.info.nodedata.NodeTransportType;
import com.phonepe.drove.models.info.nodedata.NodeType;
import com.phonepe.drove.models.instance.InstanceInfo;
import com.phonepe.drove.models.taskinstance.TaskInfo;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.eclipse.jetty.http.HttpStatus;

import javax.annotation.security.RolesAllowed;
import javax.inject.Inject;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotEmpty;
import javax.ws.rs.*;
import javax.ws.rs.core.*;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static com.phonepe.drove.auth.core.AuthConstansts.NODE_ID_HEADER;
import static javax.ws.rs.core.HttpHeaders.CONTENT_TYPE;

/**
 *
 */
@Path("/v1/logfiles")
@RolesAllowed({
        DroveUserRole.Values.DROVE_EXTERNAL_READ_ONLY_ROLE,
        DroveUserRole.Values.DROVE_EXTERNAL_READ_WRITE_ROLE,
})
@Slf4j
public class ExecutorLogFileApis {
    private final ApplicationInstanceInfoDB instanceInfoDB;
    private final TaskDB taskDB;
    private final ClusterResourcesDB clusterResourcesDB;
    private final ClusterAuthenticationConfig.SecretConfig secret;
    private final String nodeId;
    private final CloseableHttpClient httpClient;

    @Inject
    public ExecutorLogFileApis(
            ApplicationInstanceInfoDB instanceInfoDB, TaskDB taskDB, ClusterResourcesDB clusterResourcesDB,
            ClusterAuthenticationConfig config,
            CloseableHttpClient httpClient) {
        this.instanceInfoDB = instanceInfoDB;
        this.taskDB = taskDB;
        this.clusterResourcesDB = clusterResourcesDB;
        this.secret = Objects.requireNonNullElse(config, ClusterAuthenticationConfig.DEFAULT)
                .getSecrets()
                .stream()
                .filter(secretConfig -> secretConfig.getNodeType().equals(NodeType.CONTROLLER))
                .findAny()
                .orElse(null);
        nodeId = CommonUtils.hostname();
        this.httpClient = httpClient;
    }

    @GET
    @Path("/applications/{appId}/{instanceId}/list")
    public Response listAppFiles(
            @PathParam("appId") final String appId,
            @PathParam("instanceId") final String instanceId) {
        return callUpstreamForAppLogs(appId,
                                      instanceId,
                                      "/list",
                                      Map.of(),
                                      Map.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON));
    }

    @GET
    @Path("/applications/{appId}/{instanceId}/read/{fileName}")
    @Metered
    public Response streamAppLogs(
            @PathParam("appId") @NotEmpty final String appId,
            @PathParam("instanceId") @NotEmpty final String instanceId,
            @PathParam("fileName") @NotEmpty final String fileName,
            @QueryParam("offset") @Min(-1) @DefaultValue("-1") final long offset,
            @QueryParam("length") @Min(-1) @Max(Long.MAX_VALUE) @DefaultValue("-1") final int length) {
        return callUpstreamForAppLogs(appId,
                                      instanceId,
                                      "/read/" + fileName,
                                      Map.of("offset", offset, "length", length),
                                      Map.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON));
    }

    @GET
    @Path("/applications/{appId}/{instanceId}/download/{fileName}")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    @Metered
    public Response downloadAppLogFile(
            @PathParam("appId") @NotEmpty final String appId,
            @PathParam("instanceId") @NotEmpty final String instanceId,
            @PathParam("fileName") @NotEmpty final String fileName) {
        return callUpstreamForAppLogs(appId,
                                      instanceId,
                                      "/download/" + fileName,
                                      Map.of(),
                                      Map.of(HttpHeaders.CONTENT_TYPE,
                                   MediaType.TEXT_PLAIN,
                                   HttpHeaders.CONTENT_DISPOSITION,
                                   "attachment; filename=" + fileName));
    }

    @GET
    @Path("/tasks/{sourceAppName}/{taskId}/list")
    public Response listTaskLogFiles(
            @PathParam("sourceAppName") final String sourceAppName,
            @PathParam("taskId") final String taskId) {
        return callUpstreamForTaskLogs(sourceAppName,
                                      taskId,
                                      "/list",
                                      Map.of(),
                                      Map.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON));
    }

    @GET
    @Path("/tasks/{sourceAppName}/{taskId}/read/{fileName}")
    @Metered
    public Response streamTaskLogs(
            @PathParam("sourceAppName") @NotEmpty final String sourceAppName,
            @PathParam("taskId") @NotEmpty final String taskId,
            @PathParam("fileName") @NotEmpty final String fileName,
            @QueryParam("offset") @Min(-1) @DefaultValue("-1") final long offset,
            @QueryParam("length") @Min(-1) @Max(Long.MAX_VALUE) @DefaultValue("-1") final int length) {
        return callUpstreamForTaskLogs(sourceAppName,
                                       taskId,
                                      "/read/" + fileName,
                                      Map.of("offset", offset, "length", length),
                                      Map.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON));
    }

    @GET
    @Path("/tasks/{sourceAppName}/{taskId}/download/{fileName}")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    @Metered
    public Response downloadTaskLogFile(
            @PathParam("sourceAppName") @NotEmpty final String sourceAppName,
            @PathParam("taskId") @NotEmpty final String taskId,
            @PathParam("fileName") @NotEmpty final String fileName) {
        return callUpstreamForTaskLogs(sourceAppName,
                                       taskId,
                                      "/download/" + fileName,
                                      Map.of(),
                                      Map.of(HttpHeaders.CONTENT_TYPE,
                                   MediaType.TEXT_PLAIN,
                                   HttpHeaders.CONTENT_DISPOSITION,
                                   "attachment; filename=" + fileName));
    }

    private Response callUpstreamForAppLogs(
            String appId,
            String instanceId,
            String path,
            Map<String, Object> queryParams,
            Map<String, String> responseHeaders) {
        val executorHostInfo = executorNodeForApp(appId, instanceId).orElse(null);
        if(null == executorHostInfo) {
            return Response.noContent().build();
        }
        return callUpStream(appId, instanceId, path, queryParams, responseHeaders, executorHostInfo);
    }
    
    private Response callUpstreamForTaskLogs(
            String sourceAppName,
            String taskId,
            String path,
            Map<String, Object> queryParams,
            Map<String, String> responseHeaders) {
        val executorHostInfo = executorNodeForTask(sourceAppName, taskId).orElse(null);
        if(null == executorHostInfo) {
            return Response.noContent().build();
        }
        return callUpStream(sourceAppName, taskId, path, queryParams, responseHeaders, executorHostInfo);
    }

    private Optional<ExecutorNodeData> executorNodeForApp(String appId, String instanceId) {
        return instanceInfoDB.instance(appId, instanceId).map(InstanceInfo::getExecutorId)
                .flatMap(clusterResourcesDB::currentSnapshot)
                .map(ExecutorHostInfo::getNodeData);
    }

    private Optional<ExecutorNodeData> executorNodeForTask(String sourceAppName, String taskId) {
        return taskDB.task(sourceAppName, taskId)
                .map(TaskInfo::getExecutorId)
                .flatMap(clusterResourcesDB::currentSnapshot)
                .map(ExecutorHostInfo::getNodeData);
    }

    private Response callUpStream(
            String appId,
            String instanceId,
            String path,
            Map<String, Object> queryParams,
            Map<String, String> responseHeaders,
            ExecutorNodeData executorHostInfo) {
        if (null == executorHostInfo) {
            throw new WebApplicationException(Response.status(Response.Status.BAD_REQUEST).build());
        }
        val url = String.format("%s://%s:%d/apis/v1/logs/filestream/%s/%s%s",
                                executorHostInfo.getTransportType() == NodeTransportType.HTTP
                                ? "http"
                                : "https",
                                executorHostInfo.getHostname(),
                                executorHostInfo.getPort(),
                                appId,
                                instanceId,
                                path);
        val uriBuilder = UriBuilder.fromPath(url);
        Objects.<Map<String, Object>>requireNonNullElse(queryParams, Map.of())
                .forEach(uriBuilder::queryParam);
        val request = new HttpGet(uriBuilder.build());
        request.setHeader(CONTENT_TYPE, "application/json");
        request.setHeader(NODE_ID_HEADER, nodeId);
        if (null != secret) {
            request.setHeader(ClusterCommHeaders.CLUSTER_AUTHORIZATION, secret.getSecret());
        }
        val so = new StreamingOutput() {
            @Override
            @SneakyThrows
            public void write(OutputStream output) throws IOException, WebApplicationException {
                try(val r = httpClient.executeOpen(null, request, null)) {
                    val statusCode = r.getCode();
                    if (statusCode != HttpStatus.OK_200) {
                        throw new WebApplicationException(Response.serverError()
                                                                  .entity(Map.of("error",
                                                                                 "Executor call returned: " + statusCode
                                                                                 + " body: " + EntityUtils.toString(r.getEntity())))
                                                                  .build());
                    }
                    r.getEntity().getContent().transferTo(output);
                }
                output.flush();
            }
        };
        val responseBuilder = Response.ok(so);
        Objects.<Map<String, String>>requireNonNullElse(responseHeaders, Map.of())
                .forEach(responseBuilder::header);
        return responseBuilder.build();
    }
}
