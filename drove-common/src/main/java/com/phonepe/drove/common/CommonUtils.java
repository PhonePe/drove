/*
 *  Copyright (c) 2024 Original Author(s), PhonePe India Pvt. Ltd.
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

package com.phonepe.drove.common;

import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.regex.Pattern;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.phonepe.drove.common.coverageutils.IgnoreInJacocoGeneratedReport;
import com.phonepe.drove.common.discovery.Constants;
import com.phonepe.drove.common.model.ApplicationInstanceSpec;
import com.phonepe.drove.common.model.DeploymentUnitSpec;
import com.phonepe.drove.common.model.DeploymentUnitSpecVisitor;
import com.phonepe.drove.common.model.LocalServiceInstanceSpec;
import com.phonepe.drove.common.model.TaskInstanceSpec;
import com.phonepe.drove.common.retry.CompositeRetrySpec;
import com.phonepe.drove.common.retry.IntervalRetrySpec;
import com.phonepe.drove.common.retry.MaxDurationRetrySpec;
import com.phonepe.drove.common.retry.MaxRetriesRetrySpec;
import com.phonepe.drove.common.retry.RetryOnAllExceptionsSpec;
import com.phonepe.drove.common.retry.RetrySpec;
import com.phonepe.drove.common.retry.RetrySpecVisitor;
import com.phonepe.drove.models.application.JobType;
import com.phonepe.drove.models.application.checks.HTTPCheckModeSpec;
import com.phonepe.drove.models.common.ClusterState;
import com.phonepe.drove.models.common.ClusterStateData;
import com.phonepe.drove.models.common.HTTPVerb;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.classic.methods.HttpPut;
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.ClientTlsStrategyBuilder;
import org.apache.hc.client5.http.ssl.HostnameVerificationPolicy;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.client5.http.ssl.TlsSocketStrategy;
import org.apache.hc.client5.http.ssl.TrustAllStrategy;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.pool.PoolConcurrencyPolicy;
import org.apache.hc.core5.pool.PoolReusePolicy;
import org.apache.hc.core5.ssl.SSLContexts;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;

import dev.failsafe.Failsafe;
import dev.failsafe.RetryPolicy;
import dev.failsafe.event.EventListener;
import dev.failsafe.event.ExecutionCompletedEvent;
import dev.failsafe.function.CheckedPredicate;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;
import lombok.val;
import lombok.extern.slf4j.Slf4j;

/**
 *
 */
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class CommonUtils {
    private static final String DEFAULT_NAMESPACE = "drove";
    private static final Pattern NON_ALNUM_NON_DASH = Pattern.compile("[^a-z0-9-]+");

    @IgnoreInJacocoGeneratedReport
    @SuppressWarnings("deprecation")
    public static void configureMapper(ObjectMapper objectMapper) {
        objectMapper.registerModule(new ParameterNamesModule());
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_EMPTY);
        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        objectMapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
        objectMapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        objectMapper.enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS);
    }

    @IgnoreInJacocoGeneratedReport
    public static String hostname() {
        val hostname = Objects.requireNonNullElseGet(readHostname(), () -> System.getenv("HOSTNAME"));
        Objects.requireNonNull(hostname, "Hostname cannot be empty");
        return hostname;
    }


    @IgnoreInJacocoGeneratedReport
    public static String executorId(int port, String seededHostname) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(seededHostname),
                                    "Cannot generate executor ID as hostname is empty");
        return UUID.nameUUIDFromBytes((seededHostname + ":" + port).getBytes()).toString();
    }

    @IgnoreInJacocoGeneratedReport

    public static <T> List<T> sublist(final List<T> list, int start, int size) {
        if (list == null || list.isEmpty()) {
            return Collections.emptyList();
        }
        val listSize = list.size();
        if (listSize < start + 1) {
            return Collections.emptyList();
        }
        val end = Math.min(listSize, start + size);
        return list.subList(start, end);
    }

    public static <T> RetryPolicy<T> policy(final RetrySpec spec, final CheckedPredicate<T> resultChecker) {
        val policy = RetryPolicy.<T>builder();
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
                policy.withMaxRetries(maxRetries.getMaxRetries());
                return null;
            }

            @Override
            public Void visit(RetryOnAllExceptionsSpec exceptionRetry) {
                policy.handle(Exception.class);
                return null;
            }
        });
        if (null != resultChecker) {
            policy.handleResultIf(resultChecker);
        }
        return policy.build();
    }

    public static boolean isInMaintenanceWindow(final ClusterStateData clusterState) {
        if (null != clusterState) {
            val checktimeSkipWindowEnd = new Date(clusterState.getUpdated().getTime()
                                                          + 2 * Constants.EXECUTOR_REFRESH_INTERVAL.toMillis());
            return clusterState.getState().equals(ClusterState.MAINTENANCE)
                    || new Date().before(checktimeSkipWindowEnd);
        }
        return false;
    }

    @IgnoreInJacocoGeneratedReport
    private static String readHostname() {
        try {
            return InetAddress.getLocalHost().getCanonicalHostName();
        }
        catch (UnknownHostException e) {
            log.error("Error getting hostname: {}", e.getMessage(), e);
        }
        return null;
    }

    public static String instanceId(final DeploymentUnitSpec deploymentUnitSpec) {
        return deploymentUnitSpec.accept(new DeploymentUnitSpecVisitor<>() {
            @Override
            public String visit(ApplicationInstanceSpec instanceSpec) {
                return instanceSpec.getInstanceId();
            }

            @Override
            public String visit(TaskInstanceSpec taskInstanceSpec) {
                return taskInstanceSpec.getInstanceId();
            }

            @Override
            public String visit(LocalServiceInstanceSpec localServiceInstanceSpec) {
                return localServiceInstanceSpec.getInstanceId();
            }
        });
    }

    public static JobType jobType(final DeploymentUnitSpec deploymentUnitSpec) {
        return deploymentUnitSpec.accept(new DeploymentUnitSpecVisitor<>() {
            @Override
            public JobType visit(ApplicationInstanceSpec instanceSpec) {
                return JobType.SERVICE;
            }

            @Override
            public JobType visit(TaskInstanceSpec taskInstanceSpec) {
                return JobType.COMPUTATION;
            }

            @Override
            public JobType visit(LocalServiceInstanceSpec localServiceInstanceSpec) {
                return JobType.LOCAL_SERVICE;
            }
        });
    }

    @SneakyThrows
    public static CloseableHttpClient createHttpClient(boolean insecure) {
        val connectionTimeout = Duration.ofSeconds(1);
        val cmBuilder = PoolingHttpClientConnectionManagerBuilder.create();
        if (insecure) {
            log.debug("Creating insecure http client");
            cmBuilder.setTlsSocketStrategy(buildInsecureTlsStrategy());
        }
        val connectionManager = cmBuilder.build();
        connectionManager.setDefaultMaxPerRoute(100);
        connectionManager.setMaxTotal(Integer.MAX_VALUE);
        connectionManager.setDefaultConnectionConfig(ConnectionConfig.custom()
                                                             .setConnectTimeout(Timeout.of(connectionTimeout))
                                                             .setSocketTimeout(Timeout.of(connectionTimeout))
                                                             .setValidateAfterInactivity(TimeValue.ofSeconds(10))
                                                             .setTimeToLive(TimeValue.ofHours(1))
                                                             .build());
        val rc = RequestConfig.custom()
                .setConnectionRequestTimeout(Timeout.of(connectionTimeout))
                .setResponseTimeout(Timeout.of(connectionTimeout))
                .build();
        return HttpClients.custom()
                .disableRedirectHandling()
                .setConnectionManager(connectionManager)
                .setDefaultRequestConfig(rc)
                .build();
    }

    @SneakyThrows
    public static CloseableHttpClient createInternalHttpClient(HTTPCheckModeSpec httpSpec, Duration requestTimeOut) {
        val connectionTimeout = Duration.ofMillis(
                Objects.requireNonNullElse(httpSpec.getConnectionTimeout(),
                                           io.dropwizard.util.Duration.seconds(1))
                        .toMilliseconds());

        val cmBuilder = PoolingHttpClientConnectionManagerBuilder.create()
                .setPoolConcurrencyPolicy(PoolConcurrencyPolicy.STRICT)
                .setConnPoolPolicy(PoolReusePolicy.LIFO);

        if (httpSpec.isInsecure()) {
            log.debug("Creating insecure http client for internal check");
            cmBuilder.setTlsSocketStrategy(buildInsecureTlsStrategy());
        }

        val connManager = cmBuilder.build();
        
        connManager.setDefaultConnectionConfig(ConnectionConfig.custom()
                .setConnectTimeout(Timeout.of(connectionTimeout))
                .setSocketTimeout(Timeout.of(connectionTimeout))
                .setValidateAfterInactivity(TimeValue.ofSeconds(10))
                .setTimeToLive(TimeValue.ofHours(1))
                .build());

        val rc = RequestConfig.custom()
                .setResponseTimeout(Timeout.of(requestTimeOut))
                .build();

        return HttpClients.custom()
                .disableRedirectHandling() 
                .setConnectionManager(connManager)
                .setDefaultRequestConfig(rc)
                .build();
    }

    public static HttpUriRequestBase buildRequest(
            HTTPVerb verb,
            URI uri,
            String payload) {
        return switch (verb) {
            case GET -> new HttpGet(uri);
            case POST -> {
                val req = new HttpPost(uri);
                if (!Strings.isNullOrEmpty(payload)) {
                    req.setEntity(new StringEntity(payload));
                }
                yield req;
            }
            case PUT -> {
                val req = new HttpPut(uri);
                if (!Strings.isNullOrEmpty(payload)) {
                    req.setEntity(new StringEntity(payload));
                }
                yield req;
            }
        };
    }

    public static boolean waitForAction(
            RetryPolicy<Boolean> retryPolicy,
            BooleanSupplier action) {
        return waitForAction(retryPolicy,
                             action,
                             e -> {
                                 //Nothing to do here
                             });
    }

    @SuppressWarnings("java:S1874")
    public static boolean waitForAction(
            RetryPolicy<Boolean> retryPolicy,
            BooleanSupplier action,
            EventListener<ExecutionCompletedEvent<Boolean>> failureObserver) {
        return Failsafe.with(retryPolicy)
                .onFailure(failureObserver)
                .get(action::getAsBoolean);
    }

    public static boolean isRunningOnMacOS() {
        val osName = ManagementFactory.getOperatingSystemMXBean().getName();
        return !Strings.isNullOrEmpty(osName) && osName.toLowerCase().contains("mac");
    }

    public static String normalizeGPUModel(String value) {
        if (value == null) {
            return null;
        }

        return NON_ALNUM_NON_DASH.matcher(value.toLowerCase().trim())
                .replaceAll(" ")
                .trim().replaceAll("\\s+", "_");
    }

    @SneakyThrows
    private static TlsSocketStrategy buildInsecureTlsStrategy() {
        val sslContext = SSLContexts.custom()
                .loadTrustMaterial(new TrustAllStrategy())
                .build();
        return ClientTlsStrategyBuilder.create()
                .setSslContext(sslContext)
                .setHostVerificationPolicy(HostnameVerificationPolicy.CLIENT)
                .setHostnameVerifier(NoopHostnameVerifier.INSTANCE)
                .buildClassic();
    }
}
