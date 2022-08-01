package com.phonepe.drove.hazelcast.discovery;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import com.google.common.base.Strings;
import com.hazelcast.cluster.Address;
import com.hazelcast.logging.ILogger;
import com.hazelcast.spi.discovery.AbstractDiscoveryStrategy;
import com.hazelcast.spi.discovery.DiscoveryNode;
import com.hazelcast.spi.discovery.SimpleDiscoveryNode;
import lombok.SneakyThrows;
import lombok.val;

import java.net.UnknownHostException;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class DroveDiscoveryStrategy extends AbstractDiscoveryStrategy {
    public static final String TOKEN_PROPERTY="discovery.drove.token";
    private static final String AUTH_TOKEN_ENV_VARIABLE_NAME = "DROVE_APP_INSTANCE_AUTH_TOKEN";
    private static final String PROPERTY_PREFIX = "discovery.drove";

    private final ILogger logger;
    private final DroveServiceDiscoveryManager serviceDiscoveryManager;

    @SneakyThrows
    public DroveDiscoveryStrategy(ILogger logger,
                                  Map<String, Comparable> properties) {
        super(logger, properties);
        logger.info("Starting Drove Strategy");
        val droveEndpoint = this.<String>getOrNull(PROPERTY_PREFIX, DroveDiscoveryConfiguration.DROVE_ENDPOINT);
        val authToken = readToken();
        logger.fine("Auth token received as : " + authToken);
        Objects.requireNonNull(authToken, "Drove authToken cannot be empty!!!");
        val portName = this.<String>getOrNull(PROPERTY_PREFIX, DroveDiscoveryConfiguration.PORT_NAME);
        this.logger = logger;
        this.serviceDiscoveryManager = new DroveServiceDiscoveryManager(createObjectMapper(), droveEndpoint, authToken, portName, logger);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                this.serviceDiscoveryManager.stop();
            }
            catch (Exception e) {
                logger.severe("Error adding shutdown hook!", e);
            }
        }));
    }

    private String readToken() {
        val envP = System.getenv(AUTH_TOKEN_ENV_VARIABLE_NAME);
        return Strings.isNullOrEmpty(envP)
                ? System.getProperty(TOKEN_PROPERTY)
               : envP;
    }

    private ObjectMapper createObjectMapper() {
        val objectMapper = new ObjectMapper();
        objectMapper.registerModule(new ParameterNamesModule());
        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_EMPTY);
        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        objectMapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
        objectMapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        objectMapper.enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS);
        return objectMapper;
    }

    @Override
    public Iterable<DiscoveryNode> discoverNodes() {
        return serviceDiscoveryManager.getAllNodes()
                .stream()
                .map(n -> {
                    val attributes = Map.of("hostname", n.getHost());
                    try {
                        logger.fine("adding node : " + n.getHost() + ":" + n.getPort());
                        return new SimpleDiscoveryNode(new Address(n.getHost(), n.getPort()), attributes);
                    }
                    catch (UnknownHostException e) {
                        logger.severe("Error adding discovered member", e);
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }
}