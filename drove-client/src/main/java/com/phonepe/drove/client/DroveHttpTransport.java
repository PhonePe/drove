package com.phonepe.drove.client;

import java.net.URI;
import java.util.List;
import java.util.Map;

/**
 *
 */
public interface DroveHttpTransport extends AutoCloseable {

    record TransportRequest(DroveClient.Method method, URI uri, Map<String, List<String>> headers, String body) { }

    default <T> T execute(final TransportRequest request, DroveClient.ResponseHandler<T> responseHandler) {
        return switch (request.method) {
            case GET -> get(request.uri(), request.headers(), responseHandler);
            case POST -> post(request.uri(), request.headers(), request.body(), responseHandler);
            case PUT -> put(request.uri(), request.headers(), request.body(), responseHandler);
            case DELETE -> delete(request.uri(), request.headers(), responseHandler);
        };
    }

    <T> T get(
            URI uri,
            Map<String, List<String>> headers,
            DroveClient.ResponseHandler<T> responseHandler);

    <T> T post(
            URI uri,
            Map<String, List<String>> headers,
            String body,
            DroveClient.ResponseHandler<T> responseHandler);

    <T> T put(
            URI uri,
            Map<String, List<String>> headers,
            String body,
            DroveClient.ResponseHandler<T> responseHandler);

    <T> T delete(
            URI uri,
            Map<String, List<String>> headers,
            DroveClient.ResponseHandler<T> responseHandler);
}
