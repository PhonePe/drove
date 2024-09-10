package com.phonepe.drove.ignite.discovery;

import com.google.common.base.Strings;
import com.phonepe.drove.client.DroveClient;
import com.phonepe.drove.client.DroveClientConfig;
import com.phonepe.drove.client.DroveHttpTransport;
import com.phonepe.drove.client.transport.basic.DroveHttpNativeTransport;
import com.phonepe.drove.common.CommonUtils;
import com.phonepe.drove.common.retry.CompositeRetrySpec;
import com.phonepe.drove.common.retry.IntervalRetrySpec;
import com.phonepe.drove.common.retry.MaxDurationRetrySpec;
import com.phonepe.drove.common.retry.MaxRetriesRetrySpec;
import com.phonepe.drove.ignite.discovery.config.DroveIgniteConfig;
import dev.failsafe.Failsafe;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import java.io.Closeable;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
public class DroveIgniteInstanceHelper implements Closeable {

    private final DroveClient droveClient;

    private static final String SPLIT_DELIMITER = ",";

    private static final String API_PATH = "/apis/v1/internal/instances";

    private final boolean useAppNameForClustering;

    private static final String AUTH_TOKEN_ENV_VARIABLE_NAME = "DROVE_APP_INSTANCE_AUTH_TOKEN";

    @SneakyThrows
    public DroveIgniteInstanceHelper(final DroveIgniteConfig droveIgniteConfig) {
        this.useAppNameForClustering = droveIgniteConfig.isUseAppNameForDiscovery();
        val authToken = readToken();
        Objects.requireNonNull(authToken, "DrovePeerApiCall authToken cannot be empty");

        val transport = transport(droveIgniteConfig.getTransportName());
        val parsedEndpoints = parseEndpointSpec(droveIgniteConfig.getDroveEndpoint());
        if (parsedEndpoints.isEmpty()) {
            throw new IllegalArgumentException("No endpoints specified");
        }

        val clientConfig = DroveClientConfig.builder()
                .endpoints(parsedEndpoints)
                .build();
        this.droveClient = new DroveClient(
                clientConfig,
                List.of(request -> request.headers()
                        .putAll(Map.of("Content-Type", List.of("application/json"),
                                "Accept", List.of("application/json"),
                                "App-Instance-Authorization", List.of(authToken)))),
                createTransport(transport, clientConfig)
        );
        ensureConnected(droveIgniteConfig.getLeaderElectionMaxRetryDuration());
    }

    @SuppressWarnings("java:S1874")
    private void ensureConnected(final Duration maxRetryDuration) {
        val retrySpec = new CompositeRetrySpec(
                List.of(
                        new IntervalRetrySpec(Duration.ofSeconds(1)),
                        new MaxRetriesRetrySpec(-1),
                        new MaxDurationRetrySpec(
                                Objects.requireNonNullElse(maxRetryDuration, Duration.ofSeconds(30)))));
        val retryPolicy = CommonUtils.<Boolean>policy(
                retrySpec,
                r -> r);
        Failsafe.with(retryPolicy).get(() -> this.droveClient.leader().isEmpty());
    }

    @Override
    public void close() {
        droveClient.close();
        log.info("Drove apache ignite client shut down");
    }

    public <T> T findCurrentInstances(final DroveClient.ResponseHandler<T> responseHandler) {
        val path = API_PATH + (useAppNameForClustering ? "?forApp=true" : "");
        val request = new DroveClient.Request(DroveClient.Method.GET, path);
        return droveClient.execute(request, responseHandler);
    }

    private DroveHttpTransport createTransport(
            Constructor<?> transport,
            DroveClientConfig clientConfig) throws InstantiationException, IllegalAccessException, InvocationTargetException {
        if (transport != null) {
            log.info("Creating transport of type: " + transport);
            return (DroveHttpTransport) transport.newInstance(clientConfig);
        }
        log.warn("No transport is specified. Using native transport. This is not recommended for production.");
        return new DroveHttpNativeTransport(clientConfig);
    }


    private List<String> parseEndpointSpec(final String droveEndpoint) {
        val endpoints = droveEndpoint.split(SPLIT_DELIMITER);
        if ("".equals(droveEndpoint) || endpoints.length == 0) {
            log.info("No drove endpoint found for ignite discovery!!");
            return List.of();
        }
        return Arrays.asList(endpoints);
    }


    private Constructor<?> transport(final String transportName) throws NoSuchMethodException, ClassNotFoundException {
        return Strings.isNullOrEmpty(transportName)
                ? null
                : getClass().getClassLoader().loadClass(transportName).getConstructor(DroveClientConfig.class);
    }

    private String readToken() {
        return System.getenv(AUTH_TOKEN_ENV_VARIABLE_NAME);
    }
}
