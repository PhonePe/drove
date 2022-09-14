package com.phonepe.drove.client;

import java.net.http.HttpRequest;

/**
 *
 */
public interface RequestDecorator {
    void decorateRequest(final HttpRequest.Builder request);
}
