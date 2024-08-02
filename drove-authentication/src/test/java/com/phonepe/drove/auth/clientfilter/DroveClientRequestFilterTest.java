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

package com.phonepe.drove.auth.clientfilter;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.phonepe.drove.auth.core.AuthConstants;
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
                        .withHeader(AuthConstants.NODE_ID_HEADER, equalTo("test-node"))
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