package com.phonepe.drove.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import com.phonepe.drove.common.zookeeper.ZkConfig;
import lombok.experimental.UtilityClass;
import lombok.val;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.RetryForever;

import java.util.Objects;

/**
 *
 */
@UtilityClass
public class CommonUtils {
    private static final String DEFAULT_NAMESPACE = "drove";

    public static void configureMapper(ObjectMapper objectMapper) {
        objectMapper.registerModule(new ParameterNamesModule());
        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_EMPTY);
        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        objectMapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
        objectMapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    public static String hostname(String defaultValue) {
        val hostname = Objects.requireNonNullElse(System.getenv("HOSTNAME"), defaultValue);
        Objects.requireNonNull(hostname, "Hostname cannot be empty");
        return hostname;
    }

    public static CuratorFramework buildCurator(ZkConfig config) {
        return CuratorFrameworkFactory.builder()
                     .connectString(config.getConnectionString())
                     .namespace(Objects.requireNonNullElse(config.getNameSpace(), DEFAULT_NAMESPACE))
                     .retryPolicy(new RetryForever(1000))
                     .build();
    }
}
