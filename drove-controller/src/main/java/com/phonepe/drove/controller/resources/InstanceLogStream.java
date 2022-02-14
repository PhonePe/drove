package com.phonepe.drove.controller.resources;

import com.phonepe.drove.common.CommonUtils;
import com.phonepe.drove.common.auth.ClusterAuthenticationConfig;
import com.phonepe.drove.common.auth.DroveClientRequestFilter;
import com.phonepe.drove.controller.resourcemgmt.ClusterResourcesDB;
import com.phonepe.drove.controller.resourcemgmt.ExecutorHostInfo;
import com.phonepe.drove.controller.statedb.ApplicationStateDB;
import com.phonepe.drove.models.info.nodedata.NodeTransportType;
import com.phonepe.drove.models.info.nodedata.NodeType;
import com.phonepe.drove.models.instance.InstanceInfo;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.glassfish.jersey.media.sse.*;

import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.sse.SseEventSink;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

/**
 *
 */
@Produces(SseFeature.SERVER_SENT_EVENTS)
@Slf4j
@Path("/v1/logs")
public class InstanceLogStream {
    private final ApplicationStateDB applicationStateDB;
    private final ClusterResourcesDB clusterResourcesDB;
    private final ClusterAuthenticationConfig.SecretConfig secret;
    private final String nodeId;

    @Inject
    public InstanceLogStream(
            ApplicationStateDB applicationStateDB,
            ClusterResourcesDB clusterResourcesDB,
            ClusterAuthenticationConfig clusterAuthenticationConfig) {
        this.applicationStateDB = applicationStateDB;
        this.clusterResourcesDB = clusterResourcesDB;
        this.secret = clusterAuthenticationConfig.getSecrets()
                .stream()
                .filter(s -> s.getNodeType().equals(NodeType.CONTROLLER))
                .findAny()
                .orElse(null);
        nodeId = CommonUtils.hostname();
    }

    @GET
    @Path("/{appId}/{instanceId}")
    public void streamLogs(
            @Context SseEventSink sseEventSink,
            @PathParam("appId") final String appId,
            @PathParam("instanceId") final String instanceId) {
        val executorHostInfo = applicationStateDB.instance(appId, instanceId).map(InstanceInfo::getExecutorId)
                .flatMap(clusterResourcesDB::currentSnapshot)
                .map(ExecutorHostInfo::getNodeData)
                .orElse(null);
        if (null == executorHostInfo) {
            throw new WebApplicationException(Response.status(Response.Status.BAD_REQUEST).build());
        }
        val url = String.format("%s://%s:%d/apis/v1/logs/%s/%s",
                                executorHostInfo.getTransportType() == NodeTransportType.HTTP ? "http" : "https",
                                executorHostInfo.getHostname(),
                                executorHostInfo.getPort(),
                                appId,
                                instanceId);
        val prop = System.getProperty("jdk.internal.httpclient.disableHostnameVerification");
        val clientBuilder = ClientBuilder.newBuilder()
                .register(SseFeature.class)
                .register(new DroveClientRequestFilter(nodeId, secret.getSecret()));
        if("".equals(prop) || Boolean.parseBoolean(prop)) {
            clientBuilder.hostnameVerifier((s, sslSession) -> true);
        }
        val client = clientBuilder.build();
        val target = client.target(url);
        val es = new EventSource(target);
        val stopped = new AtomicBoolean();
        val streamLock = new ReentrantLock();
        val cond = streamLock.newCondition();
        val listener = new EventListener() {
            @Override
            public void onEvent(InboundEvent inboundEvent) {
                try {
                    sseEventSink.send(new OutboundEvent.Builder()
                                              .id(inboundEvent.getId())
                                              .name("LOG")
                                              .mediaType(MediaType.TEXT_PLAIN_TYPE)
                                              .data(inboundEvent.readData())
                                              .build());
                }
                catch (IllegalStateException e) {
                    streamLock.lock();
                    try {
                        stopped.set(true);
                        cond.signalAll();
                    }
                    finally {
                        streamLock.unlock();
                    }

                }
            }
        };
        es.register(listener, "LOG_GENERATED");

        streamLock.lock();
        try (sseEventSink) {
            while (!stopped.get()) {
                cond.await();
            }
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        finally {
            es.close();
            streamLock.unlock();
            log.debug("log streamer closed for {}/{}", appId, instanceId);
        }
    }
}
