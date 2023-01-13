package com.phonepe.drove.client;

import java.net.URI;
import java.util.List;
import java.util.Map;

/**
 *
 */
public interface DroveHttpTransport {

    enum Method {
        GET,
        POST,
        PUT,
        DELETE
    }

    record Request(Method method, URI uri, Map<String, List<String>> headers, String body) { }

    record Response(int statusCode, java.util.Map<String,java.util.List<String>> headers, String body) {}

    interface ResponseHandler<T> {
        T defaultValue();
        T handle(final Response response) throws Exception;
    }

    default <T> T execute(final Request request, ResponseHandler<T> responseHandler) {
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
            ResponseHandler<T> responseHandler);

    <T> T post(
            URI uri,
            Map<String, List<String>> headers,
            String body,
            ResponseHandler<T> responseHandler);

    <T> T put(
            URI uri,
            Map<String, List<String>> headers,
            String body,
            ResponseHandler<T> responseHandler);

    <T> T delete(
            URI uri,
            Map<String, List<String>> headers,
            ResponseHandler<T> responseHandler);
}
