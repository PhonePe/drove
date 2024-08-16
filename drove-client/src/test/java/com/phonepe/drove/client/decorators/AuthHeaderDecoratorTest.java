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

package com.phonepe.drove.client.decorators;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.google.common.net.HttpHeaders;
import com.phonepe.drove.client.DroveClient;
import com.phonepe.drove.client.DroveClientConfig;
import com.phonepe.drove.client.transport.basic.DroveHttpNativeTransport;
import lombok.val;
import org.apache.hc.core5.http.HttpStatus;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 *
 */
@WireMockTest
class AuthHeaderDecoratorTest {

    @Test
    void testAuthHeaderSuccess(WireMockRuntimeInfo wm) {
        runTest(wm, "AuthHeader", HttpStatus.SC_OK);
    }

    @Test
    void testAuthHeaderFail(WireMockRuntimeInfo wm) {
        runTest(wm,  "wrong", HttpStatus.SC_NOT_FOUND);
    }

    @Test
    void testAuthHeaderFailEmptyPassword(WireMockRuntimeInfo wm) {
        runTest(wm, "", HttpStatus.SC_NOT_FOUND);
    }

    private static void runTest(WireMockRuntimeInfo wm, String header, int statusCode) {
        stubFor(get(DroveClient.PING_API)
                        .withHeader(HttpHeaders.AUTHORIZATION, equalTo("AuthHeader"))
                        .willReturn(ok()));

        val clientConfig = clientConfig(wm);
        val dc = new DroveClient(clientConfig,
                                 List.of(new AuthHeaderDecorator(header)),
                                 new DroveHttpNativeTransport(clientConfig));
        assertTrue(dc.execute(new DroveClient.Request(DroveClient.Method.GET, DroveClient.PING_API),
                              new DroveClient.ResponseHandler<Boolean>() {
                                  @Override
                                  public Boolean defaultValue() {
                                      return false;
                                  }

                                  @Override
                                  public Boolean handle(DroveClient.Response response) throws Exception {
                                      return response.statusCode() == statusCode;
                                  }
                              }));
    }



    private static DroveClientConfig clientConfig(WireMockRuntimeInfo wm) {
        return new DroveClientConfig(List.of(wm.getHttpBaseUrl()),
                                     Duration.ofSeconds(1),
                                     Duration.ofSeconds(1),
                                     Duration.ofSeconds(1));
    }
}