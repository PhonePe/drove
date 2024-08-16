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

package com.phonepe.drove.client.transport.httpcomponent;

import com.phonepe.drove.client.DroveClient;
import com.phonepe.drove.client.DroveClientConfig;
import com.phonepe.drove.client.DroveHttpTransport;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.hc.client5.http.classic.methods.*;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.socket.ConnectionSocketFactory;
import org.apache.hc.client5.http.socket.PlainConnectionSocketFactory;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.URIScheme;
import org.apache.hc.core5.http.config.RegistryBuilder;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.apache.hc.core5.http.io.SocketConfig;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.pool.PoolConcurrencyPolicy;
import org.apache.hc.core5.pool.PoolReusePolicy;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 *
 */
@Slf4j
public class DroveHttpComponentsTransport implements DroveHttpTransport {


    private final CloseableHttpClient httpClient;

    public DroveHttpComponentsTransport(DroveClientConfig clientConfig) {
        this(clientConfig, buildClient(clientConfig));
    }

    @SuppressWarnings("unused")
    public DroveHttpComponentsTransport(
            DroveClientConfig clientConfig,
            CloseableHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public void close() {
        if (null != httpClient) {
            try {
                httpClient.close();
            }
            catch (IOException e) {
                log.error("Error shutting down http client: " + e.getMessage(), e);
            }
        }
    }

    @Override
    public <T> T get(URI uri, Map<String, List<String>> headers, DroveClient.ResponseHandler<T> responseHandler) {
        log.debug("Making GET call to {}", uri);
        val request = new HttpGet(uri);
        return executeRequest(headers, responseHandler, request);
    }

    @Override
    public <T> T post(
            URI uri,
            Map<String, List<String>> headers,
            String body,
            DroveClient.ResponseHandler<T> responseHandler) {
        log.debug("Making POST call to {}", uri);
        val request = new HttpPost(uri);
        request.setEntity(new StringEntity(body));
        return executeRequest(headers, responseHandler, request);
    }

    @Override
    public <T> T put(
            URI uri,
            Map<String, List<String>> headers,
            String body,
            DroveClient.ResponseHandler<T> responseHandler) {
        log.debug("Making PUT call to {}", uri);
        val request = new HttpPut(uri);
        request.setEntity(new StringEntity(body));
        return executeRequest(headers, responseHandler, request);
    }

    @Override
    public <T> T delete(URI uri, Map<String, List<String>> headers, DroveClient.ResponseHandler<T> responseHandler) {
        log.debug("Making DELETE call to {}", uri);
        val request = new HttpDelete(uri);
        return executeRequest(headers, responseHandler, request);
    }

    private static CloseableHttpClient buildClient(final DroveClientConfig clientConfig) {
        val socketFactoryRegistry = RegistryBuilder.<ConnectionSocketFactory>create()
                .register(URIScheme.HTTP.id, PlainConnectionSocketFactory.getSocketFactory())
                .register(URIScheme.HTTPS.id, SSLConnectionSocketFactory.getSocketFactory())
                .build();
        val connManager = new PoolingHttpClientConnectionManager(
                socketFactoryRegistry, PoolConcurrencyPolicy.STRICT, PoolReusePolicy.LIFO, TimeValue.ofMinutes(5));

        connManager.setDefaultSocketConfig(SocketConfig.custom()
                                                   .setTcpNoDelay(true)
                                                   .setSoTimeout(Timeout.of(Objects.requireNonNullElse(clientConfig.getOperationTimeout(),
                                                                                                       Duration.ofSeconds(
                                                                                                               2))))
                                                   .build());
        // Validate connections after 10 sec of inactivity
        connManager.setDefaultConnectionConfig(ConnectionConfig.custom()
                                                       .setConnectTimeout(Timeout.of(Objects.requireNonNullElse(
                                                               clientConfig.getConnectionTimeout(),
                                                               Duration.ofSeconds(2))))
                                                       .setSocketTimeout(Timeout.of(Objects.requireNonNullElse(
                                                               clientConfig.getOperationTimeout(),
                                                               Duration.ofSeconds(2))))
                                                       .setValidateAfterInactivity(TimeValue.ofSeconds(10))
                                                       .setTimeToLive(TimeValue.ofHours(1))
                                                       .build());
        val rc = RequestConfig.custom()
                .setResponseTimeout(Timeout.of(Objects.requireNonNullElse(clientConfig.getOperationTimeout(),
                                                                          Duration.ofSeconds(2))))
                .build();
        return HttpClients.custom()
                .setConnectionManager(connManager)
                .setDefaultRequestConfig(rc)
                .build();
    }

    private <T> T executeRequest(
            Map<String, List<String>> headers,
            DroveClient.ResponseHandler<T> responseHandler,
            HttpUriRequestBase request) {
        if (null != headers && !headers.isEmpty()) {
            headers.forEach((name, values) -> values.forEach(value -> request.setHeader(name, value)));
        }
        try {
            return httpClient.execute(request, new HttpClientResponseHandler<>() {
                @Override
                @SneakyThrows
                public T handleResponse(ClassicHttpResponse response) {
                    val headers = Arrays.stream(response.getHeaders())
                            .collect(Collectors.groupingBy(Header::getName,
                                                           Collectors.mapping(Header::getValue,
                                                                              Collectors.toUnmodifiableList())));
                    return responseHandler.handle(new DroveClient.Response(response.getCode(),
                                                                           headers,
                                                                           EntityUtils.toString(response.getEntity())));
                }
            });
        }
        catch (Exception e) {
            log.error("Error calling drove: " + e.getMessage(), e);
        }
        return responseHandler.defaultValue();
    }

}
