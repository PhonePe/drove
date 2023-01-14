package com.phonepe.drove.client;

import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import java.io.Closeable;
import java.net.URI;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 *
 */
@Slf4j
public class DroveClient implements Closeable {
    public enum Method {
        GET,
        POST,
        PUT,
        DELETE
    }

    public interface ResponseHandler<T> {
        T defaultValue();
        T handle(final Response response) throws Exception;
    }

    public record Request(Method method, String api, Map<String, List<String>> headers,
                          String body) {
        public Request(Method method, String api) {
            this(method, api, new HashMap<>(), null);
        }

        public Request(Method method, String api, String body) {
            this(method, api, new HashMap<>(), body);
        }
    }

    public static final String PING_API = "/apis/v1/ping";

    private final DroveClientConfig clientConfig;
    private final List<RequestDecorator> decorators;

    @Getter
    private final DroveHttpTransport transport;

    private final AtomicReference<String> leader = new AtomicReference<>();
    private final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
    private final ScheduledFuture<?> schedF;

    public DroveClient(
            final DroveClientConfig clientConfig,
            final List<RequestDecorator> decorators,
            DroveHttpTransport transport) {
        this.clientConfig = clientConfig;
        this.transport = transport;
        this.decorators = Objects.requireNonNullElse(decorators, List.of());

        val droveControllers = clientConfig.getEndpoints();
        if (droveControllers.size() == 1) {
            log.info("Only one drove controller provided. Leader determination will be turned off");
            leader.set(droveControllers.get(0));
            this.schedF = null;
        }
        else {
            log.debug("Starting leader determination for drove controllers: {}", droveControllers);
            this.schedF = this.executorService.scheduleWithFixedDelay(this::ensureLeader,
                                                                      0,
                                                                      Objects.requireNonNullElse(clientConfig.getCheckInterval(),
                                                                                                 Duration.ofSeconds(5))
                                                                              .toMillis(),
                                                                      TimeUnit.MILLISECONDS);
        }
    }

    public Optional<String> leader() {
        return Optional.ofNullable(leader.get());
    }

    public <T> T execute(
            final Request request,
            final ResponseHandler<T> responseHandler) {
        return leader()
                .map(currentLeader -> {
                    decorators.forEach(requestDecorator -> requestDecorator.decorateRequest(request));
                    return transport.execute(transformRequest(request, currentLeader),
                                             responseHandler);
                })
                .orElseGet(() -> {
                    log.error("No leader found for cluster");
                    return responseHandler.defaultValue();
                });
    }

    @SneakyThrows
    @Override
    public void close() {
        if (schedF == null) {
            log.info("No leader determination running. Nothing needs to be done");
            return;
        }
        schedF.cancel(true);
        executorService.shutdown();
        transport.close();
        log.info("Drove client shut down");
    }

    private static DroveHttpTransport.TransportRequest transformRequest(Request request, String endpoint) {
        return new DroveHttpTransport.TransportRequest(request.method(),
                                                       URI.create(endpoint + request.api()),
                                                       request.headers(),
                                                       request.body());
    }

    private static class PingCheckResponseHandler implements ResponseHandler<Boolean> {
        @Override
        public Boolean defaultValue() {
            return false;
        }

        @Override
        public Boolean handle(Response response) {
            return response.statusCode() == 200;
        }
    }

    private void ensureLeader() {
        if (leader.get() == null) {
            log.warn("No leader set, trying to find leader");
            findLeaderFromEndpoints();
        }
        else {
            if (isLeader(leader.get())) {
                log.debug("Drove controller {} is still the leader", leader.get());
            }
            else {
                log.warn("Current leader {} is invalid. Trying to find new leader", leader.get());
                leader.set(null);
                findLeaderFromEndpoints();
            }
        }
    }

    private void findLeaderFromEndpoints() {
        log.debug("Endpoints to be tested: {}", clientConfig.getEndpoints());
        val newLeader = clientConfig.getEndpoints()
                .stream()
                .filter(this::isLeader)
                .findFirst()
                .orElse(null);
        if (null == newLeader) {
            log.error("Could not find a leader controller");
            return;
        }
        leader.set(newLeader);
        log.info("Drove controller {} is the new leader", leader);
    }

    private boolean isLeader(final String endpoint) {
        log.debug("Checking endpoint for leadership: {}", endpoint);
        val request = new Request(Method.GET, PING_API);
        decorators.forEach(decorator -> decorator.decorateRequest(request));
        try {
            return transport.execute(transformRequest(request, endpoint), new PingCheckResponseHandler());
        }
        catch (Exception e) {
            log.error("Error making call to drove to find leader: " + e.getMessage(), e);
        }
        return false;
    }

    public static final record Response(int statusCode, Map<String, List<String>> headers, String body) {}
}
