package com.phonepe.drove.auth.clientfilter;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.phonepe.drove.auth.core.AuthConstansts;
import com.phonepe.drove.auth.model.ClusterCommHeaders;
import lombok.val;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.ws.rs.client.ClientBuilder;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 *
 */
@WireMockTest
class DroveClientRequestFilterTest {

    @BeforeEach
    void setupStubs() {
        stubFor(get("/").willReturn(unauthorized()));
        stubFor(get("/")
                        .withHeader(AuthConstansts.NODE_ID_HEADER, equalTo("test-node"))
                        .withHeader(ClusterCommHeaders.CLUSTER_AUTHORIZATION, equalTo("test-secret"))
                        .willReturn(ok()));
    }

    @Test
    void testAuth(WireMockRuntimeInfo wmRuntime) {
        val client = ClientBuilder.newBuilder()
                .register(new DroveClientRequestFilter("test-node", "test-secret"))
                .build();
        setupStubs();

        assertEquals(HttpStatus.OK_200,
                     client.target(wmRuntime.getHttpBaseUrl())
                .request()
                .get()
                .getStatus());
    }

    @Test
    void testAuthFail(WireMockRuntimeInfo wmRuntime) {
        val client = ClientBuilder.newBuilder()
                .register(new DroveClientRequestFilter("test-node", "wrong-secret"))
                .build();
        setupStubs();

        assertEquals(HttpStatus.UNAUTHORIZED_401,
                     client.target(wmRuntime.getHttpBaseUrl())
                .request()
                .get()
                .getStatus());
    }


}