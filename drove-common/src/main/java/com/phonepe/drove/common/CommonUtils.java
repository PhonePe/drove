package com.phonepe.drove.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import com.phonepe.drove.common.zookeeper.ZkConfig;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.RetryForever;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 *
 */
@UtilityClass
@Slf4j
public class CommonUtils {
    private static final String DEFAULT_NAMESPACE = "drove";

    public static void configureMapper(ObjectMapper objectMapper) {
        objectMapper.registerModule(new ParameterNamesModule());
        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_EMPTY);
        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        objectMapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
        objectMapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    public static String hostname() {
        val hostname = Objects.requireNonNullElseGet(readHostname(), () -> System.getenv("HOSTNAME"));
        Objects.requireNonNull(hostname, "Hostname cannot be empty");
        return hostname;
    }


    public static String executorId(int port) {
        return UUID.nameUUIDFromBytes((hostname() + ":" + port).getBytes()).toString();
    }

    public static CuratorFramework buildCurator(ZkConfig config) {
        return CuratorFrameworkFactory.builder()
                     .connectString(config.getConnectionString())
                     .namespace(Objects.requireNonNullElse(config.getNameSpace(), DEFAULT_NAMESPACE))
                     .retryPolicy(new RetryForever(1000))
                     .build();
    }

    public static <T> List<T> sublist(final List<T> list, int start, int size) {
        if(list == null || list.isEmpty()) {
            return Collections.emptyList();
        }
        val listSize = list.size();
        if(listSize  < start + 1) {
            return Collections.emptyList();
        }
        val end  = Math.min(listSize, start + size);
        return list.subList(start, end);
    }

    private static String readHostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        }
        catch (UnknownHostException e) {
            log.error("Error getting hostname: " + e.getMessage(), e);
        }
        return null;
    }
}
