package com.phonepe.drove.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import com.phonepe.drove.common.retry.*;
import com.phonepe.drove.common.zookeeper.ZkConfig;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import net.jodah.failsafe.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.RetryForever;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Predicate;

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
        objectMapper.enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS);
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
            return InetAddress.getLocalHost().getCanonicalHostName();
        }
        catch (UnknownHostException e) {
            log.error("Error getting hostname: " + e.getMessage(), e);
        }
        return null;
    }

    public static <T> RetryPolicy<T> policy(final RetrySpec spec, final Predicate<T> resultChecker) {
        val policy = new RetryPolicy<T>();
        spec.accept(new RetrySpecVisitor<Void>() {
            @Override
            public Void visit(CompositeRetrySpec composite) {
                Objects.requireNonNullElse(composite.getSpecs(), Collections.<RetrySpec>emptyList())
                        .forEach(rs -> rs.accept(this));
                return null;
            }

            @Override
            public Void visit(IntervalRetrySpec interval) {
                policy.withDelay(interval.getInterval());
                return null;
            }

            @Override
            public Void visit(MaxDurationRetrySpec maxDuration) {
                policy.withMaxDuration(maxDuration.getMaxDuration());
                return null;
            }

            @Override
            public Void visit(MaxRetriesRetrySpec maxRetries) {
                policy.withMaxAttempts(maxRetries.getMaxRetries());
                return null;
            }

            @Override
            public Void visit(RetryOnAllExceptionsSpec exceptionRetry) {
                policy.handle(Exception.class);
                return null;
            }
        });
        if(null != resultChecker) {
            policy.handleResultIf(resultChecker);
        }
        return policy;
    }
}
