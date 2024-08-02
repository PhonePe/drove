/*
 *  Copyright (c) 2022 Original Author(s), PhonePe India Pvt. Ltd.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.phonepe.drove.hazelcast.discovery;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Strings;
import com.hazelcast.cluster.Address;
import com.hazelcast.logging.ILogger;
import com.hazelcast.spi.discovery.DiscoveryNode;
import com.hazelcast.spi.discovery.SimpleDiscoveryNode;
import com.phonepe.drove.client.DroveClient;
import com.phonepe.drove.client.DroveClientConfig;
import com.phonepe.drove.client.DroveHttpTransport;
import com.phonepe.drove.client.transport.basic.DroveHttpNativeTransport;
import com.phonepe.drove.models.api.ApiErrorCode;
import com.phonepe.drove.models.api.ApiResponse;
import com.phonepe.drove.models.instance.InstanceInfo;
import com.phonepe.drove.models.instance.InstancePort;
import lombok.SneakyThrows;
import lombok.val;

import java.io.Closeable;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.UnknownHostException;
import java.util.*;

/**
 *
 */
public class DrovePeerTracker implements Closeable {
    private static final String SPLIT_DELIMITER = ",";
    @SuppressWarnings("java:S1075")
    private static final String API_PATH = "/apis/v1/internal/instances";

    private final String portName;
    private final ILogger log;
    private final ObjectMapper mapper;
    private final boolean useAppNameForClustering;
    private final DroveClient client;


    @SneakyThrows
    public DrovePeerTracker(
            final String endpoints,
            final String token,
            final String portName,
            final ILogger log,
            final ObjectMapper mapper,
            final boolean useAppNameForClustering,
            final Constructor<?> transport) {
        this.portName = portName;
        this.log = log;
        this.mapper = mapper;
        this.useAppNameForClustering = useAppNameForClustering;
        val parsedEndpoints = parseEndpointSpec(endpoints);
        if (parsedEndpoints.isEmpty()) {
            throw new IllegalArgumentException("No endpoints specified");
        }
        val clientConfig = DroveClientConfig.builder()
                .endpoints(parsedEndpoints)
                .build();
        this.client = new DroveClient(
                clientConfig,
                List.of(request -> request.headers()
                        .putAll(Map.of("Content-Type", List.of("application/json"),
                                       "Accept", List.of("application/json"),
                                       "App-Instance-Authorization", List.of(token)))),
                createTransport(transport, clientConfig, log)
        );
    }

    private static DroveHttpTransport createTransport(
            Constructor<?> transport,
            DroveClientConfig clientConfig,
            ILogger log) throws InstantiationException, IllegalAccessException, InvocationTargetException {
        if (transport != null) {
            log.info("Creating transport of type: " + transport);
            return (DroveHttpTransport) transport.newInstance(clientConfig);
        }
        log.warning("No transport is specified. Using native transport. This is not recommended for production.");
        return new DroveHttpNativeTransport(clientConfig);
    }

    public List<DiscoveryNode> peers() {
        return findCurrentPeers().orElse(List.of());
    }

    @Override
    public void close() {
        client.close();
        log.info("Drove peer discovery shut down");
    }

    private class PeerResponseTransformer implements DroveClient.ResponseHandler<Optional<List<DiscoveryNode>>> {
        @Override
        public Optional<List<DiscoveryNode>> defaultValue() {
            return Optional.empty();
        }

        @Override
        public Optional<List<DiscoveryNode>> handle(DroveClient.Response response) throws Exception {
            if (response.statusCode() != 200 || Strings.isNullOrEmpty(response.body())) {
                log.severe("Could not find peers. Error: " + response.statusCode() + ": " + response.body());
                return Optional.empty();
            }
            val apiData = mapper.readValue(response.body(),
                                           new TypeReference<ApiResponse<List<InstanceInfo>>>() {
                                           });
            if (!apiData.getStatus().equals(ApiErrorCode.SUCCESS)) {
                log.severe("Could not read peer list. Api call unsuccessful with error: " + apiData.getMessage());
                return Optional.empty();
            }
            log.fine("Drove Response Data: " + apiData);
            return Optional.of(apiData.getData()
                                       .stream()
                                       .<DiscoveryNode>map(this::translate)
                                       .filter(Objects::nonNull)
                                       .toList());
        }

        private SimpleDiscoveryNode translate(InstanceInfo info) {
            val hostname = info.getLocalInfo().getHostname();
            val portInfo =
                    Objects.requireNonNullElse(info.getLocalInfo()
                                                       .getPorts(),
                                               Map.<String, InstancePort>of())
                            .get(portName);
            if (null == portInfo) {
                log.severe("No port found with port name: " + portName + " on app instance " + info.getInstanceId());
                return null;
            }
            try {
                val attributes = Map.of("instanceId", info.getInstanceId(),
                                        "executorId", info.getExecutorId(),
                                    "hostname", info.getLocalInfo().getHostname());
                return new SimpleDiscoveryNode(new Address(hostname, portInfo.getHostPort()), attributes);
            }
            catch (UnknownHostException e) {
                log.severe("Could not create node representation. Error: " + e.getMessage(), e);
                return null;

            }
        }
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
        val path = API_PATH + (useAppNameForClustering? "?forApp=true" : "");
        val request = new DroveClient.Request(DroveClient.Method.GET, path);
        return client.execute(request, new PeerResponseTransformer());
    }
}
