package com.phonepe.drove.executor.checker;

import com.phonepe.drove.common.CommonUtils;
import com.phonepe.drove.executor.model.ExecutorInstanceInfo;
import com.phonepe.drove.executor.utils.ExecutorUtils;
import com.phonepe.drove.models.application.CheckResult;
import com.phonepe.drove.models.application.checks.CheckMode;
import com.phonepe.drove.models.application.checks.CheckSpec;
import com.phonepe.drove.models.application.checks.HTTPCheckModeSpec;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.io.entity.EntityUtils;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CancellationException;

/**
 * Calls the provided http endpoint on the container
 */
@Slf4j
public class HttpChecker implements Checker {
    private final CloseableHttpClient httpClient;
    private final HTTPCheckModeSpec httpSpec;
    private final URI uri;


    public HttpChecker(CheckSpec checkSpec, HTTPCheckModeSpec httpSpec, ExecutorInstanceInfo instance) {
        val requestTimeOut = Duration.ofMillis(
                Objects.requireNonNullElse(checkSpec.getTimeout(), io.dropwizard.util.Duration.seconds(1))
                        .toMilliseconds());
        this.httpClient = CommonUtils.createInternalHttpClient(httpSpec, requestTimeOut);
        this.httpSpec = httpSpec;
        val portSpec = instance.getLocalInfo().getPorts().get(httpSpec.getPortName());
        Objects.requireNonNull(portSpec, "Invalid port spec. No port of name '" + httpSpec.getPortName() + "' exists");
        this.uri = URI.create(String.format("%s://localhost:%d%s",
                                       httpSpec.getProtocol().name().toLowerCase(),
                                       portSpec.getHostPort(),
                                       httpSpec.getPath()));
        log.debug("URI for healthcheck: {}", uri);
    }

    @Override
    public CheckResult call() {
        val request = ExecutorUtils.buildRequestFromSpec(httpSpec, uri);

        try {
            return httpClient.execute(request, response -> {
                val statusCode = response.getCode();
                val responseBody = null != response.getEntity()
                                   ? EntityUtils.toString(response.getEntity())
                                   : "";
                if (httpSpec.getSuccessCodes().contains(statusCode)) {
                    return CheckResult.healthy();
                }
                log.error("HTTP check unhealthy. Status code: {} response: {}", statusCode, responseBody);
                return CheckResult.unhealthy(String.format("Response from %S: [%d] %s",
                                                           uri,
                                                           statusCode,
                                                           responseBody));                });

        }
        catch (IOException e) {
            return CheckResult.unhealthy("Healthcheck error from " + uri + ": " + e.getMessage());
        }
        catch (CancellationException e) {
            Thread.currentThread().interrupt();
        }
        return CheckResult.unhealthy("Healthcheck interrupted");
    }

    @Override
    public CheckMode mode() {
        return CheckMode.HTTP;
    }

    @Override
    public void close() throws Exception {
        httpClient.close();
        log.debug("Shut down http checker");
    }
}
