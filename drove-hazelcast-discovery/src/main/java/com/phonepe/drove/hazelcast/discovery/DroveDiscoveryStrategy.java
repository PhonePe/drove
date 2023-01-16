package com.phonepe.drove.hazelcast.discovery;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import com.google.common.base.Strings;
import com.hazelcast.logging.ILogger;
import com.hazelcast.spi.discovery.AbstractDiscoveryStrategy;
import com.hazelcast.spi.discovery.DiscoveryNode;
import com.phonepe.drove.client.DroveClientConfig;
import lombok.SneakyThrows;
import lombok.val;

import java.lang.reflect.Constructor;
import java.util.Map;
import java.util.Objects;

public class DroveDiscoveryStrategy extends AbstractDiscoveryStrategy {
    public static final String TOKEN_PROPERTY = "discovery.drove.token";
    private static final String AUTH_TOKEN_ENV_VARIABLE_NAME = "DROVE_APP_INSTANCE_AUTH_TOKEN";
    private static final String PROPERTY_PREFIX = "discovery.drove";

    private final DrovePeerTracker peerTracker;


    @SneakyThrows
    public DroveDiscoveryStrategy(
            ILogger logger,
            Map<String, Comparable> properties) {
        super(logger, properties);
        logger.info("Starting DrovePeerApiCall Strategy");
        val droveEndpoint = this.<String>getOrNull(PROPERTY_PREFIX, DroveDiscoveryConfiguration.DROVE_ENDPOINT);
        val authToken = readToken();
        logger.fine("Auth token received as : " + authToken);
        Objects.requireNonNull(authToken, "DrovePeerApiCall authToken cannot be empty!!!");
        val portName = this.<String>getOrNull(PROPERTY_PREFIX, DroveDiscoveryConfiguration.PORT_NAME);
        val transportName = this.<String>getOrNull(PROPERTY_PREFIX, DroveDiscoveryConfiguration.TRANSPORT);
        val transport = transport(transportName);
        this.peerTracker = new DrovePeerTracker(droveEndpoint, authToken, portName, logger, createObjectMapper(), transport);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                this.peerTracker.close();
            }
            catch (Exception e) {
                logger.severe("Error adding shutdown hook!", e);
            }
        }));
    }

    @Override
    public Iterable<DiscoveryNode> discoverNodes() {
        return peerTracker.peers();
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


    @SuppressWarnings("java:S2658")
    private Constructor<?> transport(String transportName) throws NoSuchMethodException, ClassNotFoundException {
        return Strings.isNullOrEmpty(transportName)
               ? null
               : getClass().getClassLoader().loadClass(transportName).getConstructor(DroveClientConfig.class);
    }
}
