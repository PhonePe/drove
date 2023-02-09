package com.phonepe.drove.executor.discovery;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.phonepe.drove.auth.config.ClusterAuthenticationConfig;
import com.phonepe.drove.auth.model.ClusterCommHeaders;
import com.phonepe.drove.executor.managed.ExecutorIdManager;
import com.phonepe.drove.models.api.ApiResponse;
import com.phonepe.drove.models.info.nodedata.NodeTransportType;
import com.phonepe.drove.models.info.nodedata.NodeType;
import com.phonepe.drove.models.internal.KnownInstancesData;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.HttpResponse;
import org.apache.http.client.RedirectStrategy;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicHeader;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.EntityUtils;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;

/**
 *
 */
@Singleton
@Slf4j
public class ClusterClient {
    private final ExecutorIdManager executorIdManager;
    private final ManagedLeadershipObserver leadershipObserver;
    private final ObjectMapper mapper;

    private final CloseableHttpClient httpClient;

    @Inject
    public ClusterClient(
            ExecutorIdManager executorIdManager, ManagedLeadershipObserver leadershipObserver,
            ObjectMapper mapper,
            ClusterAuthenticationConfig clusterAuthenticationConfig) {
        this.executorIdManager = executorIdManager;
        this.leadershipObserver = leadershipObserver;
        this.mapper = mapper;
        this.httpClient = buildHttpClient(clusterAuthenticationConfig);
    }

    public KnownInstancesData lastKnownInstances() {
        val executorId = executorIdManager.executorId().orElse(null);
        if (null == executorId) {
            log.info("Executor Id not yet available. Cannot fetch last state data from controller.");
            return KnownInstancesData.EMPTY;
        }
        val leader = leadershipObserver.leader().orElse(null);
        if (null == leader) {
            log.info("Leader not found for cluster. Cannot fetch last state data from controller.");
            return KnownInstancesData.EMPTY;
        }
        try {
            val uri = String.format("%s://%s:%d/apis//v1/internal/cluster/executors/" + executorId + "/instances",
                                    leader.getTransportType() == NodeTransportType.HTTP
                                    ? "http"
                                    : "https",
                                    leader.getHostname(),
                                    leader.getPort());
            val request = new HttpGet(uri);
            val response = httpClient.execute(request, new ResponseHandler<ApiResponse<KnownInstancesData>>() {
                @Override
                public ApiResponse<KnownInstancesData> handleResponse(HttpResponse response) throws IOException {
                    if (response.getStatusLine().getStatusCode() == 200) {
                        return mapper.readValue(EntityUtils.toByteArray(response.getEntity()),
                                                new TypeReference<>() {});
                    }
                    log.error("Error fetching last known status from leader. Received response: [{}] {}",
                              response.getStatusLine().getStatusCode(), EntityUtils.toString(response.getEntity()));
                    return null;
                }
            });
            if (null == response || null == response.getData()) {
                return KnownInstancesData.EMPTY;
            }
            return response.getData();

        }
        catch (Exception e) {
            log.error("Could not fetch last known instances data. Error " + e.getMessage(), e);
        }
        return KnownInstancesData.EMPTY;
    }

    private CloseableHttpClient buildHttpClient(ClusterAuthenticationConfig clusterAuthenticationConfig) {
        val authSecret = clusterAuthenticationConfig.getSecrets()
                .stream()
                .filter(s -> s.getNodeType().equals(NodeType.EXECUTOR))
                .findAny()
                .map(ClusterAuthenticationConfig.SecretConfig::getSecret)
                .orElse(null);
        return HttpClients.custom()
                .addInterceptorFirst((HttpRequestInterceptor) (httpRequest, httpContext)
                        -> httpRequest.addHeader(new BasicHeader(ClusterCommHeaders.CLUSTER_AUTHORIZATION, authSecret)))
                .setRedirectStrategy(new RedirectStrategy() {
                    @Override
                    public boolean isRedirected(
                            HttpRequest request,
                            HttpResponse response,
                            HttpContext context) {
                        return false;
                    }

                    @Override
                    public HttpUriRequest getRedirect(
                            HttpRequest request,
                            HttpResponse response,
                            HttpContext context) {
                        return null;
                    }
                })
                .build();
    }

}
