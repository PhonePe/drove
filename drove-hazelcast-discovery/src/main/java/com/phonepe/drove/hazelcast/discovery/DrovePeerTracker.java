package com.phonepe.drove.hazelcast.discovery;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hazelcast.cluster.Address;
import com.hazelcast.logging.ILogger;
import com.hazelcast.spi.discovery.DiscoveryNode;
import com.hazelcast.spi.discovery.SimpleDiscoveryNode;
import com.phonepe.drove.client.DroveClient;
import com.phonepe.drove.client.DroveClientConfig;
import com.phonepe.drove.models.api.ApiErrorCode;
import com.phonepe.drove.models.api.ApiResponse;
import com.phonepe.drove.models.instance.InstanceInfo;
import com.phonepe.drove.models.instance.InstancePort;
import lombok.val;

import java.io.Closeable;
import java.io.IOException;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

/**
 *
 */
public class DrovePeerTracker implements Closeable {
    private static final String SPLIT_DELIMITER = ",";
    @SuppressWarnings("java:S1075")
    private static final String API_PATH = "/apis/v1/internal/instances";

    private final String token;
    private final String portName;
    private final ILogger log;
    private final ObjectMapper mapper;
    private final DroveClient client;

    public DrovePeerTracker(
            final String endpoints,
            final String token,
            final String portName,
            final ILogger log,
            final ObjectMapper mapper) {
        this.token = token;
        this.portName = portName;
        this.log = log;
        this.mapper = mapper;
        val parsedEndpoints = parseEndpointSpec(endpoints);
        if (parsedEndpoints.isEmpty()) {
            throw new IllegalArgumentException("No endpoints specified");
        }
        this.client = new DroveClient(
                DroveClientConfig.builder()
                        .endpoints(parsedEndpoints)
                        .build(),
                List.of());
    }

    public List<DiscoveryNode> peers() {
        return findCurrentPeers().orElse(List.of());
    }

    private List<String> parseEndpointSpec(final String droveEndpoint) {
        val endpoints = droveEndpoint.split(SPLIT_DELIMITER);
        if ("".equals(droveEndpoint) || endpoints.length == 0) {
            log.info("No drove endpoint found for hazelcast discovery!!");
            return List.of();
        }
        return Arrays.asList(endpoints);
    }

    private Optional<List<DiscoveryNode>> findCurrentPeers() {
        val leader = client.leader().orElse(null);
        if (null == leader) {
            log.severe("No leader found for drove cluster.");
            return Optional.empty();
        }
        val requestBuilder = HttpRequest.newBuilder(URI.create(leader + API_PATH));
        val request = requestBuilder.GET()
                .header("Content-Type", "application/json")
                .header("App-Instance-Authorization", token)
                .timeout(Duration.ofSeconds(3))
                .build();
        try {
            val response = client.getHttpClient()
                    .send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.severe("Could not find peers. Error: " + response.statusCode() + ": " + response.body());
                return Optional.empty();
            }
            val apiData = mapper.readValue(response.body(), new TypeReference<ApiResponse<List<InstanceInfo>>>() {});
            if (!apiData.getStatus().equals(ApiErrorCode.SUCCESS)) {
                log.severe("Could not read peer list. Api call unsuccessful with error: " + apiData.getMessage());
                return Optional.empty();
            }
            log.fine("Drove Response Data: " + apiData);
            return Optional.of(apiData.getData()
                                       .stream()
                                       .map(info -> {
                                           val hostname = info.getLocalInfo().getHostname();
                                           val portInfo = Objects.requireNonNullElse(info.getLocalInfo().getPorts(),
                                                                                     Map.<String, InstancePort>of())
                                                   .get(portName);
                                           if (null == portInfo) {
                                               log.severe("No port found with port name: " + portName + " on app " +
                                                                  "instance " + info.getInstanceId());
                                               return null;
                                           }
                                           try {
                                               val attributes = Map.of(
                                                       "instanceId", info.getInstanceId(),
                                                       "executorId", info.getExecutorId(),
                                                       "hostname", info.getLocalInfo().getHostname());
                                               return (DiscoveryNode) new SimpleDiscoveryNode(new Address(hostname,
                                                                                                          portInfo.getHostPort()),
                                                                                              attributes);
                                           }
                                           catch (UnknownHostException e) {
                                               log.severe("Could not create node represenation. Error: " + e.getMessage(),
                                                          e);
                                               return null;

                                           }
                                       })
                                       .filter(Objects::nonNull)
                                       .toList());
        }
        catch (IOException e) {
            log.severe("Could not find peers. Error: " + e.getMessage(), e);
        }
        catch (InterruptedException e) {
            log.info("Api call to controller interrupted");
            Thread.currentThread().interrupt();
        }
        return Optional.empty();
    }

    @Override
    public void close() throws IOException {
        client.close();
        log.info("Drove peer discovery shut down");
    }
}
