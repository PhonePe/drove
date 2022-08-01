package com.phonepe.drove.hazelcast.discovery;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hazelcast.logging.ILogger;
import com.phonepe.drove.hazelcast.discovery.client.Drove;
import com.phonepe.drove.hazelcast.discovery.client.DroveClient;
import com.phonepe.drove.hazelcast.discovery.client.DroveClientProvider;
import com.phonepe.drove.hazelcast.discovery.exception.DroveException;
import com.phonepe.drove.models.instance.InstancePort;
import lombok.Value;
import lombok.val;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class DroveServiceDiscoveryManager {
    private static final String SPLIT_DELIMITER = ",";

    private final AtomicReference<List<ServiceNode>> serviceNodes = new AtomicReference<>(List.of());

    private final ILogger log;

    private final ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();

    @Value
    public static class ServiceNode {
        String host;
        int port;
    }


    public DroveServiceDiscoveryManager(
            final ObjectMapper objectMapper,
            final String droveEndpoint,
            final String authToken,
            final String portName,
            final ILogger logger) throws DroveException {
        this.log = logger;
        val drovePoller = new DrovePoller(log, new DroveClientProvider(parseEndpointSpec(droveEndpoint, objectMapper)),
                                          serviceNodes, authToken, portName);
        this.scheduledExecutorService.scheduleAtFixedRate(drovePoller, 0, 10, TimeUnit.SECONDS);
    }

    public List<ServiceNode> getAllNodes() {
        log.info("listing these nodes " + serviceNodes.get().toString());
        return serviceNodes.get();
    }

    public void stop() {
        scheduledExecutorService.shutdown();
    }

    private static class DrovePoller implements Runnable {

        private final ILogger log;
        private final DroveClientProvider clientProvider;
        private final AtomicReference<List<ServiceNode>> serviceNodes;

        private final String token;

        private final String portName;

        public DrovePoller(
                ILogger log, final DroveClientProvider clientProvider,
                AtomicReference<List<ServiceNode>> serviceNodes,
                final String token,
                final String portName) {
            this.log = log;
            this.clientProvider = clientProvider;
            this.serviceNodes = serviceNodes;
            this.token = token;
            this.portName = portName;
        }

        @Override
        public void run() {
            try {
                refreshInstanceList(0);
            } catch (Exception e) {
                log.severe("Error getting app metadata from drove", e);
            }
        }

        private void refreshInstanceList(int tries) throws Exception {
            if (tries >= clientProvider.size()) {
                throw new DroveException(400, "Failed to connect to Drove Cluster!!!");
            }
            try {
                log.fine("Trying to discover nodes for app ");
                val response = clientProvider.getCurrentActiveClient().getAppInstances(token);
                log.fine("Response from drove " + response.toString());
                val nodes = response.getData().stream()
                        .map(info -> {
                            val hostname = info.getLocalInfo().getHostname();
                            val portInfo = Objects.requireNonNullElse(info.getLocalInfo().getPorts(),
                                                                      Map.<String, InstancePort>of()).get(portName);
                            if(null == portInfo) {
                                log.severe("No port found with port name: " + portName + " on app instance " + info.getInstanceId());
                                return null;
                            }
                            return new ServiceNode(hostname, portInfo.getHostPort());
                        })
                        .filter(Objects::nonNull)
                        .toList();
                nodes.forEach(n -> log.info("Fetched node: " + n.getHost() + ":" + n.getPort()));
                serviceNodes.getAndSet(nodes);
            }
            catch (DroveException e) {
                if (e.getStatus() == 400) {
                    log.info("Got 400 on drove request, Retrying on other host!!!");
                    clientProvider.incrementActiveHostIndex();
                    refreshInstanceList(++tries);
                }
                else {
                    log.severe("Got error while calling Drove", e);
                }
            }
        }
    }

    private List<Drove> parseEndpointSpec(final String droveEndpoint, final ObjectMapper objectMapper) throws DroveException {
        val endpoints = droveEndpoint.split(SPLIT_DELIMITER);
        if (endpoints.length == 0) {
            throw new DroveException(400, "No drove endpoint found for hazelcast discovery!!");
        }
        return Arrays.stream(endpoints)
                .map(endpoint -> DroveClient.getInstance(objectMapper, endpoint.trim()))
                .toList();
    }
}
