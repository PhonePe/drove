package com.phonepe.drove.client;

/**
 *
 */
public interface RequestDecorator {
    void decorateRequest(final DroveClient.Request request);
}
