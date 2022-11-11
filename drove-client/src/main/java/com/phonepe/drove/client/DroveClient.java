package com.phonepe.drove.client;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import java.io.Closeable;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
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
    public static final String PING_API = "/apis/v1/ping";

    private final DroveClientConfig clientConfig;
    private final List<RequestDecorator> decorators;

    @Getter
    private final HttpClient httpClient;
    private final AtomicReference<String> leader = new AtomicReference<>();
    private final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
    private final ScheduledFuture<?> schedF;

    public DroveClient(
            final DroveClientConfig clientConfig,
            final List<RequestDecorator> decorators) {
        this.clientConfig = clientConfig;
        this.decorators = Objects.requireNonNullElse(decorators, List.of());
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Objects.requireNonNullElse(clientConfig.getConnectionTimeout(), Duration.ofSeconds(3)))
                .build();
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
        val uri = endpoint + PING_API;
        val requestBuilder = HttpRequest.newBuilder(URI.create(uri));
        decorators.forEach(decorator -> decorator.decorateRequest(requestBuilder));
        val request = requestBuilder.GET()
                .timeout(Objects.requireNonNullElse(clientConfig.getOperationTimeout(), Duration.ofSeconds(1)))
                .build();
        try {
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;
        }
        catch (IOException e) {
            log.error("Error making http call to " + uri + ": " + e.getMessage(), e);
        }
        catch (InterruptedException e) {
            log.error("HTTP Request interrupted");
            Thread.currentThread().interrupt();
        }
        return false;
    }

    @Override
    public void close() throws IOException {
        if(schedF == null) {
            log.info("No leader determination running. Nothing needs to be done");
            return;
        }
        schedF.cancel(true);
        executorService.shutdown();
        log.info("Drove client shut down");
    }
}
