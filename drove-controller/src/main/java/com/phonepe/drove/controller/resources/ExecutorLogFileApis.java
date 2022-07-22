package com.phonepe.drove.controller.resources;

import com.codahale.metrics.annotation.Metered;
import com.phonepe.drove.auth.config.ClusterAuthenticationConfig;
import com.phonepe.drove.auth.model.ClusterCommHeaders;
import com.phonepe.drove.common.CommonUtils;
import com.phonepe.drove.controller.resourcemgmt.ClusterResourcesDB;
import com.phonepe.drove.controller.resourcemgmt.ExecutorHostInfo;
import com.phonepe.drove.controller.statedb.ApplicationInstanceInfoDB;
import com.phonepe.drove.models.info.nodedata.NodeTransportType;
import com.phonepe.drove.models.info.nodedata.NodeType;
import com.phonepe.drove.models.instance.InstanceInfo;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.eclipse.jetty.http.HttpStatus;

import javax.annotation.security.PermitAll;
import javax.inject.Inject;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotEmpty;
import javax.ws.rs.*;
import javax.ws.rs.core.*;
import java.io.*;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;

import static com.phonepe.drove.auth.core.AuthConstansts.NODE_ID_HEADER;
import static javax.ws.rs.core.HttpHeaders.CONTENT_TYPE;

/**
 *
 */
@Path("/v1/logfiles")
@PermitAll
@Slf4j
public class ExecutorLogFileApis {
    private final ApplicationInstanceInfoDB instanceInfoDB;
    private final ClusterResourcesDB clusterResourcesDB;
    private final ClusterAuthenticationConfig.SecretConfig secret;
    private final String nodeId;
    private final HttpClient httpClient;

    @Inject
    public ExecutorLogFileApis(
            ApplicationInstanceInfoDB instanceInfoDB, ClusterResourcesDB clusterResourcesDB,
            ClusterAuthenticationConfig config) {
        this.instanceInfoDB = instanceInfoDB;
        this.clusterResourcesDB = clusterResourcesDB;
        this.secret = Objects.requireNonNullElse(config, ClusterAuthenticationConfig.DEFAULT)
                .getSecrets()
                .stream()
                .filter(secretConfig -> secretConfig.getNodeType().equals(NodeType.CONTROLLER))
                .findAny()
                .orElse(null);
        nodeId = CommonUtils.hostname();
        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofSeconds(1))
                .build();
    }

    @GET
    @Path("/{appId}/{instanceId}/list")
    public Response listFiles(
            @PathParam("appId") final String appId,
            @PathParam("instanceId") final String instanceId) {
        return callUpstream(appId,
                            instanceId,
                            "/list",
                            Map.of(),
                            Map.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON));
    }

    @GET
    @Path("/{appId}/{instanceId}/read/{fileName}")
    @Metered
    public Response streamLogs(
            @PathParam("appId") @NotEmpty final String appId,
            @PathParam("instanceId") @NotEmpty final String instanceId,
            @PathParam("fileName") @NotEmpty final String fileName,
            @QueryParam("offset") @Min(-1) @DefaultValue("-1") final long offset,
            @QueryParam("length") @Min(-1) @Max(Long.MAX_VALUE) @DefaultValue("-1") final int length) {
        return callUpstream(appId,
                            instanceId,
                            "/read/" + fileName,
                            Map.of("offset", offset, "length", length),
                            Map.of(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON));
    }

    @GET
    @Path("/{appId}/{instanceId}/download/{fileName}")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    @Metered
    public Response downloadLogFile(
            @PathParam("appId") @NotEmpty final String appId,
            @PathParam("instanceId") @NotEmpty final String instanceId,
            @PathParam("fileName") @NotEmpty final String fileName) {
        return callUpstream(appId,
                            instanceId,
                            "/download/" + fileName,
                            Map.of(),
                            Map.of(HttpHeaders.CONTENT_TYPE,
                                   MediaType.TEXT_PLAIN,
                                   HttpHeaders.CONTENT_DISPOSITION,
                                   "attachment; filename=" + fileName));
    }

    private Response callUpstream(
            String appId,
            String instanceId,
            String path,
            Map<String, Object> queryParams,
            Map<String, String> responseHeaders) {
        val executorHostInfo = instanceInfoDB.instance(appId, instanceId).map(InstanceInfo::getExecutorId)
                .flatMap(clusterResourcesDB::currentSnapshot)
                .map(ExecutorHostInfo::getNodeData)
                .orElse(null);
        if (null == executorHostInfo) {
            throw new WebApplicationException(Response.status(Response.Status.BAD_REQUEST).build());
        }
        val url = String.format("%s://%s:%d/apis/v1/logs/filestream/%s/%s" + path,
                                executorHostInfo.getTransportType() == NodeTransportType.HTTP
                                ? "http"
                                : "https",
                                executorHostInfo.getHostname(),
                                executorHostInfo.getPort(),
                                appId,
                                instanceId);
        val uriBuilder = UriBuilder.fromPath(url);
        Objects.<Map<String, Object>>requireNonNullElse(queryParams, Map.of())
                .forEach(uriBuilder::queryParam);
        val requestBuilder = HttpRequest.newBuilder(uriBuilder.build());
        requestBuilder.header(CONTENT_TYPE, "application/json");
        requestBuilder.header(NODE_ID_HEADER, nodeId);
        if (null != secret) {
            requestBuilder.header(ClusterCommHeaders.CLUSTER_AUTHORIZATION, secret.getSecret());
        }
        val request = requestBuilder.GET().build();
        val so = new StreamingOutput() {
            @Override
            public void write(OutputStream output) throws IOException, WebApplicationException {
                try {
                    val r = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
                    if (r.statusCode() != HttpStatus.OK_200) {
                        throw new WebApplicationException(Response.serverError()
                                                                  .entity(Map.of("error",
                                                                                 "Executor call returned: " + r.statusCode()))
                                                                  .build());
                    }
                    try (val bi = new BufferedReader(new InputStreamReader(r.body()), 8192);
                         val br = new BufferedWriter(new OutputStreamWriter(output), 8192)) {
                        var buf = new char[8192];
                        int c = -1;
                        while ((c = bi.read(buf, 0, 8192)) != -1) {
                            br.write(buf, 0, c);
                            br.flush();
                        }
                    }
                    catch (IOException ex) {
                        log.error("Error writing output: " + ex.getMessage(), ex);
                        throw new WebApplicationException(Response.serverError()
                                                                  .entity(Map.of("error", ex.getMessage()))
                                                                  .build());
                    }
                }
                catch (InterruptedException e) {
                    log.error("Error: ", e);
                    Thread.currentThread().interrupt();
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
