package com.phonepe.drove.executor.checker;

import com.phonepe.drove.executor.model.ExecutorInstanceInfo;
import com.phonepe.drove.executor.utils.ExecutorUtils;
import com.phonepe.drove.models.application.CheckResult;
import com.phonepe.drove.models.application.checks.CheckMode;
import com.phonepe.drove.models.application.checks.CheckSpec;
import com.phonepe.drove.models.application.checks.HTTPCheckModeSpec;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Objects;

/**
 * Calls the provided http endpoint on the container
 */
@Slf4j
public class HttpChecker implements Checker {
    private final HttpClient httpClient;
    private final HTTPCheckModeSpec httpSpec;
    private final URI uri;
    private final Duration requestTimeout;


    public HttpChecker(CheckSpec checkSpec, HTTPCheckModeSpec httpSpec, ExecutorInstanceInfo instance) {
        var connectionTimeout = Duration.ofMillis(
                Objects.requireNonNullElse(httpSpec.getConnectionTimeout(),
                                           io.dropwizard.util.Duration.seconds(1))
                        .toMilliseconds());
        httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(connectionTimeout)
                .build();
        this.httpSpec = httpSpec;
        val portSpec = instance.getLocalInfo().getPorts().get(httpSpec.getPortName());
        Objects.requireNonNull(portSpec, "Invalid port spec. No port of name '" + httpSpec.getPortName() + "' exists");
        this.uri = URI.create(String.format("%s://localhost:%d%s",
                                       httpSpec.getProtocol(),
                                       portSpec.getHostPort(),
                                       httpSpec.getPath()));
        this.requestTimeout = Duration.ofMillis(
                Objects.requireNonNullElse(checkSpec.getTimeout(), io.dropwizard.util.Duration.seconds(1))
                        .toMilliseconds());
        log.debug("URI for healthcheck: {}", uri);
    }

    @Override
    public CheckResult call() {
        val requestBuilder = ExecutorUtils.buildRequestFromSpec(httpSpec, uri);

        val request = requestBuilder.timeout(requestTimeout)
                .build();
        try {
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (httpSpec.getSuccessCodes().contains(response.statusCode())) {
                return CheckResult.healthy();
            }
            val responseBody = response.body();
            log.error("HTTP check unhealthy. Status code: {} response: {}", response.statusCode(), responseBody);
            return CheckResult.unhealthy(String.format("Response from %S: [%d] %s",
                                                       uri,
                                                       response.statusCode(),
                                                       responseBody));
        }
        catch (IOException e) {
            return CheckResult.unhealthy("Healthcheck error from " + uri + ": " + e.getMessage());
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return CheckResult.unhealthy("Healthcheck interrupted");
    }

    @Override
    public CheckMode mode() {
        return CheckMode.HTTP;
    }
}
