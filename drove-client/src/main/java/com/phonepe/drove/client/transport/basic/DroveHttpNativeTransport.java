/*
 *  Copyright (c) 2022 Original Author(s), PhonePe India Pvt. Ltd.
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

package com.phonepe.drove.client.transport.basic;

import com.google.common.base.Strings;
import com.phonepe.drove.client.DroveClient;
import com.phonepe.drove.client.DroveClientConfig;
import com.phonepe.drove.client.DroveHttpTransport;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 *
 */
@Slf4j
public class DroveHttpNativeTransport implements DroveHttpTransport {
    private final DroveClientConfig clientConfig;
    private final HttpClient httpClient;

    public DroveHttpNativeTransport(final DroveClientConfig clientConfig) {
        this(clientConfig, HttpClient.newBuilder()
                .connectTimeout(Objects.requireNonNullElse(clientConfig.getConnectionTimeout(), Duration.ofSeconds(3)))
                .build());
    }

    public DroveHttpNativeTransport(final DroveClientConfig clientConfig, HttpClient httpClient) {
        this.clientConfig = clientConfig;
        this.httpClient = httpClient;
    }

    @Override
    public void close() {
        //Nothing needs to be done here
        log.debug("HTTP Native Transport Shutdown");
    }

    @Override
    public <T> T get(
            final URI uri,
            Map<String, List<String>> headers,
            final DroveClient.ResponseHandler<T> responseHandler) {
        log.debug("Calling GET api: {}", uri);
        val requestBuilder = HttpRequest.newBuilder(uri)
                .GET()
                .timeout(Objects.requireNonNullElse(clientConfig.getOperationTimeout(), Duration.ofSeconds(1)));
        addHeaders(headers, requestBuilder);
        return handleResponse(uri, responseHandler, requestBuilder.build());
    }

    @Override
    public <T> T post(
            final URI uri,
            Map<String, List<String>> headers,
            String body,
            final DroveClient.ResponseHandler<T> responseHandler) {
        log.debug("Calling POST api: {}", uri);
        val requestBuilder = HttpRequest.newBuilder(uri)
                .POST(requestBody(body))
                .timeout(Objects.requireNonNullElse(clientConfig.getOperationTimeout(), Duration.ofSeconds(1)));
        addHeaders(headers, requestBuilder);
        return handleResponse(uri, responseHandler, requestBuilder.build());
    }

    @Override
    public <T> T put(
            final URI uri,
            Map<String, List<String>> headers,
            String body,
            final DroveClient.ResponseHandler<T> responseHandler) {
        log.debug("Calling PUT api: {}", uri);
        val requestBuilder = HttpRequest.newBuilder(uri)
                .PUT(requestBody(body))
                .timeout(Objects.requireNonNullElse(clientConfig.getOperationTimeout(), Duration.ofSeconds(1)));
        addHeaders(headers, requestBuilder);
        return handleResponse(uri, responseHandler, requestBuilder.build());
    }

    @Override
    public <T> T delete(final URI uri, Map<String, List<String>> headers, final DroveClient.ResponseHandler<T> responseHandler) {
        log.debug("Calling PUT api: {}", uri);
        val requestBuilder = HttpRequest.newBuilder(uri)
                .DELETE()
                .timeout(Objects.requireNonNullElse(clientConfig.getOperationTimeout(), Duration.ofSeconds(1)));
        addHeaders(headers, requestBuilder);
        return handleResponse(uri, responseHandler, requestBuilder.build());
    }

    private static HttpRequest.BodyPublisher requestBody(String body) {
        return Strings.isNullOrEmpty(body)
               ? HttpRequest.BodyPublishers.noBody()
               : HttpRequest.BodyPublishers.ofString(body);
    }

    private static String[] headers(Map<String, List<String>> headers) {
        val genHeaders = new ArrayList<String>();
        Objects.<Map<String, List<String>>>requireNonNullElse(headers, Map.of())
                .forEach((key, values) ->
                                 values.forEach(value -> {
                                     genHeaders.add(key);
                                     genHeaders.add(value);
                                 }));
        log.debug("Headers {}", genHeaders);
        return genHeaders.toArray(new String[genHeaders.size()]);
    }

    private <T> T handleResponse(URI uri, DroveClient.ResponseHandler<T> responseHandler, HttpRequest request) {
        try {
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return responseHandler.handle(new DroveClient.Response(response.statusCode(),
                                                                   response.headers().map(),
                                                                   response.body()));
        }
        catch (InterruptedException e) {
            log.error("HTTP TransportRequest interrupted");
            Thread.currentThread().interrupt();
        }
        catch (Exception e) {
            log.error("Error making http call to " + uri + ": " + e.getMessage(), e);
        }
        return responseHandler.defaultValue();
    }

    private static void addHeaders(Map<String, List<String>> headers, HttpRequest.Builder requestBuilder) {
        if(null != headers && !headers.isEmpty()) {
            requestBuilder.headers(headers(headers));
        }
    }

}
