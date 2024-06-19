package com.phonepe.drove.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.phonepe.drove.common.coverageutils.IgnoreInJacocoGeneratedReport;
import com.phonepe.drove.common.discovery.Constants;
import com.phonepe.drove.common.model.ApplicationInstanceSpec;
import com.phonepe.drove.common.model.DeploymentUnitSpec;
import com.phonepe.drove.common.model.DeploymentUnitSpecVisitor;
import com.phonepe.drove.common.model.TaskInstanceSpec;
import com.phonepe.drove.common.retry.*;
import com.phonepe.drove.common.zookeeper.ZkConfig;
import com.phonepe.drove.models.application.checks.HTTPCheckModeSpec;
import com.phonepe.drove.models.common.ClusterState;
import com.phonepe.drove.models.common.ClusterStateData;
import com.phonepe.drove.models.common.HTTPVerb;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import net.jodah.failsafe.event.ExecutionCompletedEvent;
import net.jodah.failsafe.function.CheckedConsumer;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.RetryForever;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.classic.methods.HttpPut;
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.protocol.RedirectStrategy;
import org.apache.hc.client5.http.socket.ConnectionSocketFactory;
import org.apache.hc.client5.http.socket.PlainConnectionSocketFactory;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactoryBuilder;
import org.apache.hc.client5.http.ssl.TrustAllStrategy;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.config.RegistryBuilder;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.pool.PoolConcurrencyPolicy;
import org.apache.hc.core5.pool.PoolReusePolicy;
import org.apache.hc.core5.ssl.SSLContextBuilder;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

/**
 *
 */
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class CommonUtils {
    private static final String DEFAULT_NAMESPACE = "drove";

    @IgnoreInJacocoGeneratedReport
    @SuppressWarnings("deprecation")
    public static void configureMapper(ObjectMapper objectMapper) {
        objectMapper.registerModule(new ParameterNamesModule());
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
    public static CuratorFramework buildCurator(ZkConfig config) {
        return CuratorFrameworkFactory.builder()
                .connectString(config.getConnectionString())
                .namespace(Objects.requireNonNullElse(config.getNameSpace(), DEFAULT_NAMESPACE))
                .retryPolicy(new RetryForever(1000))
                .sessionTimeoutMs(30_000)
                .build();
    }

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
        return policy;
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
        });
    }


    @SneakyThrows
    public static CloseableHttpClient createHttpClient(boolean insecure) {
        val connectionTimeout = Duration.ofSeconds(1);
        val cmBuilder = PoolingHttpClientConnectionManagerBuilder.create();
        if (insecure) {
            log.debug("Creating insecure http client");
            cmBuilder.setSSLSocketFactory(SSLConnectionSocketFactoryBuilder.create()
                                                  .setSslContext(
                                                          SSLContextBuilder.create()
                                                                  .loadTrustMaterial(TrustAllStrategy.INSTANCE)
                                                                  .build())
                                                  .setHostnameVerifier(NoopHostnameVerifier.INSTANCE)
                                                  .build());
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
        val socketFactoryBuilder = SSLConnectionSocketFactoryBuilder.create();
        if (httpSpec.isInsecure()) {
            socketFactoryBuilder.setSslContext(
                            SSLContextBuilder.create()
                                    .loadTrustMaterial(TrustAllStrategy.INSTANCE)
                                    .build())
                    .setHostnameVerifier(NoopHostnameVerifier.INSTANCE);
        }

        val socketFactoryRegistry = RegistryBuilder.<ConnectionSocketFactory>create()
                .register(URIScheme.HTTP.id, PlainConnectionSocketFactory.getSocketFactory())
                .register(URIScheme.HTTPS.id, socketFactoryBuilder.build())
                .build();
        val connManager = new PoolingHttpClientConnectionManager(
                socketFactoryRegistry, PoolConcurrencyPolicy.STRICT, PoolReusePolicy.LIFO, TimeValue.ofMinutes(5));
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
                .setRedirectStrategy(new RedirectStrategy() {

                    @Override
                    @IgnoreInJacocoGeneratedReport
                    public boolean isRedirected(HttpRequest request, HttpResponse response, HttpContext context) {
                        return false;
                    }

                    @Override
                    @IgnoreInJacocoGeneratedReport
                    public URI getLocationURI(HttpRequest request, HttpResponse response, HttpContext context) {
                        return null;
                    }
                })
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
            CheckedConsumer<ExecutionCompletedEvent<Boolean>> failureObserver) {
        return Failsafe.with(retryPolicy)
                .onFailure(failureObserver)
                .get(action::getAsBoolean);
    }
}
