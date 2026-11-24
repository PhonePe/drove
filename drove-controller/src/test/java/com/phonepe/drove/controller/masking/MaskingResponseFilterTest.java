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

package com.phonepe.drove.controller.masking;

import com.phonepe.drove.models.api.ApiResponse;
import com.phonepe.drove.models.application.ApplicationSpec;
import com.phonepe.drove.models.common.HTTPCallSpec;
import com.phonepe.drove.models.common.Protocol;
import com.phonepe.drove.models.config.impl.ControllerHttpFetchConfigSpec;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MaskingResponseFilterTest {

    private final MaskingResponseFilter filter = new MaskingResponseFilter();

    @Test
    @SuppressWarnings("unchecked")
    void maskingDoesNotMutateTheOriginalEntity() throws java.io.IOException {
        val spec = appSpecWithSecrets();
        val response = ApiResponse.success(spec);

        val requestContext = mock(ContainerRequestContext.class);
        val responseContext = mock(ContainerResponseContext.class);
        when(responseContext.getEntityClass()).thenReturn((Class) ApiResponse.class);
        when(responseContext.getEntity()).thenReturn(response);

        filter.filter(requestContext, responseContext);

        //The original object keeps its secrets. Only the copy is masked.
        assertEquals("s3cret-value", spec.getEnv().get("DB_PASSWORD"));
        assertEquals("super-secret-payload", httpConfigOf(spec).getHttp().getPayload());
        assertEquals("token-123", httpConfigOf(spec).getHttp().getHeaders().get("X-Api-Key"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void envValuesAreMaskedButKeysStayVisible() throws java.io.IOException {
        val spec = appSpecWithSecrets();
        val response = ApiResponse.success(spec);

        val requestContext = mock(ContainerRequestContext.class);
        val responseContext = mock(ContainerResponseContext.class);
        when(responseContext.getEntityClass()).thenReturn((Class) ApiResponse.class);
        when(responseContext.getEntity()).thenReturn(response);

        filter.filter(requestContext, responseContext);

        val maskedResponse = (ApiResponse<ApplicationSpec>) captureEntity(responseContext);
        val maskedEnv = maskedResponse.getData().getEnv();
        assertEquals("***", maskedEnv.get("DB_PASSWORD"));
        assertEquals("***", maskedEnv.get("API_TOKEN"));
        //Keys must stay visible so callers can still tell what is set.
        assertEquals(2, maskedEnv.size());
    }

    @Test
    @SuppressWarnings("unchecked")
    void maskOnHttpCallSpecFieldsHidesSecrets() throws java.io.IOException {
        val spec = appSpecWithSecrets();
        val response = ApiResponse.success(spec);

        val requestContext = mock(ContainerRequestContext.class);
        val responseContext = mock(ContainerResponseContext.class);
        when(responseContext.getEntityClass()).thenReturn((Class) ApiResponse.class);
        when(responseContext.getEntity()).thenReturn(response);

        filter.filter(requestContext, responseContext);

        val maskedResponse = (ApiResponse<ApplicationSpec>) captureEntity(responseContext);
        val maskedConfig = (ControllerHttpFetchConfigSpec) maskedResponse.getData().getConfigs().get(0);
        assertEquals("***", maskedConfig.getHttp().getPayload());
        assertEquals("***", maskedConfig.getHttp().getHeaders().get("X-Api-Key"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void maskedEntityIsADifferentInstance() throws java.io.IOException {
        val spec = appSpecWithSecrets();
        val response = ApiResponse.success(spec);

        val requestContext = mock(ContainerRequestContext.class);
        val responseContext = mock(ContainerResponseContext.class);
        when(responseContext.getEntityClass()).thenReturn((Class) ApiResponse.class);
        when(responseContext.getEntity()).thenReturn(response);

        filter.filter(requestContext, responseContext);

        val maskedResponse = (ApiResponse<ApplicationSpec>) captureEntity(responseContext);
        //Deep copy: the masked data object must not be the same instance.
        val maskedSpec = maskedResponse.getData();
        org.junit.jupiter.api.Assertions.assertNotSame(spec, maskedSpec);
    }

    @Test
    @SuppressWarnings("unchecked")
    void nonApiResponseEntitiesAreNotTouched() throws java.io.IOException {
        val requestContext = mock(ContainerRequestContext.class);
        val responseContext = mock(ContainerResponseContext.class);
        when(responseContext.getEntityClass()).thenReturn((Class) String.class);
        when(responseContext.getEntity()).thenReturn("plain");

        filter.filter(requestContext, responseContext);

        verify(responseContext, never()).setEntity(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void nullDataEntityIsHandled() throws java.io.IOException {
        val response = ApiResponse.success(null);
        val requestContext = mock(ContainerRequestContext.class);
        val responseContext = mock(ContainerResponseContext.class);
        when(responseContext.getEntityClass()).thenReturn((Class) ApiResponse.class);
        when(responseContext.getEntity()).thenReturn(response);

        filter.filter(requestContext, responseContext);

        val maskedResponse = (ApiResponse<?>) captureEntity(responseContext);
        assertNull(maskedResponse.getData());
    }

    private static Object captureEntity(ContainerResponseContext responseContext) {
        val captor = ArgumentCaptor.forClass(Object.class);
        verify(responseContext).setEntity(captor.capture());
        return captor.getValue();
    }

    private static ControllerHttpFetchConfigSpec httpConfigOf(ApplicationSpec spec) {
        return (ControllerHttpFetchConfigSpec) spec.getConfigs().get(0);
    }

    private static ApplicationSpec appSpecWithSecrets() {
        val httpCallSpec = HTTPCallSpec.builder()
                .protocol(Protocol.HTTP)
                .hostname("config.internal")
                .port(8080)
                .path("/config")
                .payload("super-secret-payload")
                .headers(Map.of("X-Api-Key", "token-123"))
                .build();
        val config = new ControllerHttpFetchConfigSpec("/files/app.conf", httpCallSpec);
        return com.phonepe.drove.controller.ControllerTestUtils.appSpec()
                .withEnv(Map.of("DB_PASSWORD", "s3cret-value", "API_TOKEN", "an0ther-token"))
                .withConfigs(List.of(config));
    }
}
