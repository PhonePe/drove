package com.phonepe.drove.executor.resources;

import com.codahale.metrics.annotation.Metered;
import com.phonepe.drove.common.auth.DroveUser;
import com.phonepe.drove.common.auth.DroveUserRole;
import com.phonepe.drove.executor.logging.LogBus;
import io.dropwizard.auth.Auth;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.glassfish.jersey.media.sse.OutboundEvent;
import org.glassfish.jersey.media.sse.SseFeature;

import javax.annotation.security.RolesAllowed;
import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.sse.SseEventSink;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 *
 */
@Produces(SseFeature.SERVER_SENT_EVENTS)
@Slf4j
@Path("/v1/logs")
@RolesAllowed(DroveUserRole.Values.DROVE_CLUSTER_NODE_ROLE)
public class LogStream {
    private final LogBus logBus;

    @Inject
    public LogStream(final LogBus logBus) {
        this.logBus = logBus;
    }

    @GET
    @Path("/{appId}/{instanceId}")
    @Metered
    public void streamLogs(
            @Auth final DroveUser user,
            @Context SseEventSink sseEventSink,
            @PathParam("appId") final String appId,
            @PathParam("instanceId") final String instanceId) {
        log.debug("Received connection request from: {}", user.getName());
        val stopped = new AtomicBoolean();
        val streamLock = new ReentrantLock();
        val cond = streamLock.newCondition();
        streamLock.lock();
        val reader = new LogReader(sseEventSink, appId, instanceId, stopped, streamLock, cond);
        try (sseEventSink) {
            logBus.registerLogHandler(reader);
            log.debug("Initialised new log streamer for {}/{}", appId, instanceId);
            while (!stopped.get()) {
                cond.await();
            }
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        finally {
            logBus.unregisterLogHandler(reader);
            streamLock.unlock();
            log.debug("log streamer closed for {}/{}", appId, instanceId);
        }
    }

    private static final class LogReader implements LogBus.LogListener {
        private final String id = UUID.randomUUID().toString();
        private final SseEventSink sink;
        private final String appId;
        private final String instanceId;
        private final AtomicBoolean stopped;
        private final ReentrantLock streamLock;
        private final Condition cond;

        private LogReader(
                SseEventSink sink,
                String appId,
                String instanceId,
                AtomicBoolean stopped, ReentrantLock streamLock, Condition cond) {
            this.sink = sink;
            this.appId = appId;
            this.instanceId = instanceId;
            this.stopped = stopped;
            this.streamLock = streamLock;
            this.cond = cond;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public void consume(LogBus.LogLine logLine) {
            if(stopped.get()) {
                return;
            }
            if(logLine.getAppId().equals(appId) && logLine.getInstanceId().equals(instanceId)) {
                try {
                    sink.send(new OutboundEvent.Builder()
                                      .name("LOG_GENERATED")
                                      .id(UUID.randomUUID().toString())
                                      .mediaType(MediaType.APPLICATION_JSON_TYPE)
                                      .data(LogBus.LogLine.class, logLine)
                                      .reconnectDelay(3000)
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
        }
    }
}
