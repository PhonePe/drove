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

package com.phonepe.drove.executor.checker;

import com.phonepe.drove.common.CommonUtils;
import com.phonepe.drove.executor.model.*;
import com.phonepe.drove.executor.utils.ExecutorUtils;
import com.phonepe.drove.models.application.CheckResult;
import com.phonepe.drove.models.application.checks.CheckMode;
import com.phonepe.drove.models.application.checks.CheckSpec;
import com.phonepe.drove.models.application.checks.HTTPCheckModeSpec;
import com.phonepe.drove.models.instance.InstancePort;
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
    private static final int MAX_READABLE_OUTPUT_SIZE_BYTES = 8192;

    private final CloseableHttpClient httpClient;
    private final HTTPCheckModeSpec httpSpec;
    private final URI uri;


    public HttpChecker(CheckSpec checkSpec, HTTPCheckModeSpec httpSpec, DeployedExecutionObjectInfo instance) {
        val requestTimeOut = Duration.ofMillis(
                Objects.requireNonNullElse(checkSpec.getTimeout(), io.dropwizard.util.Duration.seconds(1))
                        .toMilliseconds());
        this.httpClient = CommonUtils.createInternalHttpClient(httpSpec, requestTimeOut);
        this.httpSpec = httpSpec;
        val portSpec = instance.accept(new DeployedExecutorInstanceInfoVisitor<InstancePort>() {
            @Override
            public InstancePort visit(ExecutorInstanceInfo applicationInstanceInfo) {
                return applicationInstanceInfo.getLocalInfo().getPorts().get(httpSpec.getPortName());
            }

            @Override
            public InstancePort visit(ExecutorTaskInfo taskInfo) {
                return null;
            }

            @Override
            public InstancePort visit(ExecutorLocalServiceInstanceInfo localServiceInstanceInfo) {
                return localServiceInstanceInfo.getLocalInfo().getPorts().get(httpSpec.getPortName());
            }
        });
        Objects.requireNonNull(portSpec, "Invalid port spec. No port of name '" + httpSpec.getPortName() + "' exists");
        this.uri = URI.create(String.format("%s://localhost:%d%s",
                                       httpSpec.getProtocol().urlPrefix(),
                                       portSpec.getHostPort(),
                                       httpSpec.getPath()));
        log.debug("URI for healthcheck: {}", uri);
    }

    @Override
    @SuppressWarnings("deprecation")
    public CheckResult call() {
        val request = ExecutorUtils.buildRequestFromSpec(httpSpec, uri);

        try {
            return httpClient.execute(request, response -> {
                val statusCode = response.getCode();
                val responseBody = null != response.getEntity()
                                   ? EntityUtils.toString(response.getEntity(), MAX_READABLE_OUTPUT_SIZE_BYTES)
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
