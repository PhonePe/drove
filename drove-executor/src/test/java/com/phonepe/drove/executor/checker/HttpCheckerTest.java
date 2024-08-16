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

package com.phonepe.drove.executor.checker;

import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.phonepe.drove.common.CommonTestUtils;
import com.phonepe.drove.executor.ExecutorTestingUtils;
import com.phonepe.drove.models.application.CheckResult;
import com.phonepe.drove.models.application.checks.CheckMode;
import com.phonepe.drove.models.application.checks.HTTPCheckModeSpec;
import com.phonepe.drove.models.common.HTTPVerb;
import com.phonepe.drove.models.common.Protocol;
import io.dropwizard.util.Duration;
import lombok.SneakyThrows;
import lombok.val;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.concurrent.Executors;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.phonepe.drove.executor.ExecutorTestingUtils.checkSpec;
import static com.phonepe.drove.executor.ExecutorTestingUtils.testAppInstanceSpec;
import static org.junit.jupiter.api.Assertions.*;

@WireMockTest(httpsEnabled = true)
class HttpCheckerTest {

    @Test
    @SneakyThrows
    void testCheckGetSuccess(WireMockRuntimeInfo wm) {
        stubFor(get("/").willReturn(ok()));
        val info = ExecutorTestingUtils.createExecutorAppInstanceInfo(wm);
        val spec = checkSpec(HTTPVerb.GET);
        try (val checker = new HttpChecker(spec, (HTTPCheckModeSpec) spec.getMode(), info)) {
            val r = checker.call();
            assertEquals(CheckResult.Status.HEALTHY, r.getStatus());
        }
    }

    @Test
    @SneakyThrows
    void testCheckGetSuccessInsecureHttps(WireMockRuntimeInfo wm) {
        stubFor(get("/").willReturn(ok()));
        val info =
                ExecutorTestingUtils.createExecutorAppInstanceInfo(
                        testAppInstanceSpec(CommonTestUtils.APP_IMAGE_NAME, 1, true),
                        wm.getHttpsPort());
        val spec = checkSpec(HTTPVerb.GET, true);
        try (val checker = new HttpChecker(spec, (HTTPCheckModeSpec) spec.getMode(), info)) {
            val r = checker.call();
            assertEquals(CheckResult.Status.HEALTHY, r.getStatus());
        }
    }

    @Test
    @SneakyThrows
    void testCheckGetFail(WireMockRuntimeInfo wm) {
        stubFor(get("/").willReturn(serverError().withBody("Server Kaput!!")));
        val info = ExecutorTestingUtils.createExecutorAppInstanceInfo(wm);
        val spec = checkSpec(HTTPVerb.GET);
        try (val checker = new HttpChecker(spec, (HTTPCheckModeSpec) spec.getMode(), info)) {

            val r = checker.call();
            assertEquals(CheckResult.Status.UNHEALTHY, r.getStatus());
            assertTrue(r.getMessage().endsWith("Server Kaput!!"));
        }
    }

    @Test
    @SneakyThrows
    void testCheckPostSuccess(WireMockRuntimeInfo wm) {
        stubFor(post("/").willReturn(ok()));
        val info = ExecutorTestingUtils.createExecutorAppInstanceInfo(wm);
        val spec = checkSpec(HTTPVerb.POST);
        try (val checker = new HttpChecker(spec, (HTTPCheckModeSpec) spec.getMode(), info)) {

            val r = checker.call();
            assertEquals(CheckResult.Status.HEALTHY, r.getStatus());
        }
    }

    @Test
    @SneakyThrows
    void testCheckPostSuccessWithBody(WireMockRuntimeInfo wm) {
        stubFor(post("/").withRequestBody(equalTo("Hello")).willReturn(ok()));
        val info = ExecutorTestingUtils.createExecutorAppInstanceInfo(wm);
        val spec = checkSpec(HTTPVerb.POST, "Hello");
        try (val checker = new HttpChecker(spec, (HTTPCheckModeSpec) spec.getMode(), info)) {

            val r = checker.call();
            assertEquals(CheckResult.Status.HEALTHY, r.getStatus());
        }
    }

    @Test
    @SneakyThrows
    void testCheckPostFail(WireMockRuntimeInfo wm) {
        stubFor(post("/").willReturn(serverError().withBody("Server Kaput!!")));
        val info = ExecutorTestingUtils.createExecutorAppInstanceInfo(wm);
        val spec = checkSpec(HTTPVerb.POST);
        try (val checker = new HttpChecker(spec, (HTTPCheckModeSpec) spec.getMode(), info)) {

            val r = checker.call();
            assertEquals(CheckResult.Status.UNHEALTHY, r.getStatus());
            assertTrue(r.getMessage().endsWith("Server Kaput!!"));
        }
    }

    @Test
    @SneakyThrows
    void testCheckPutSuccess(WireMockRuntimeInfo wm) {
        stubFor(put("/").willReturn(ok()));
        val info = ExecutorTestingUtils.createExecutorAppInstanceInfo(wm);
        val spec = checkSpec(HTTPVerb.PUT);
        try (val checker = new HttpChecker(spec, (HTTPCheckModeSpec) spec.getMode(), info)) {
            val r = checker.call();
            assertEquals(CheckResult.Status.HEALTHY, r.getStatus());
        }
    }

    @Test
    @SneakyThrows
    void testCheckPutSuccessWithBody(WireMockRuntimeInfo wm) {
        stubFor(put("/").withRequestBody(equalTo("Hello")).willReturn(ok()));
        val info = ExecutorTestingUtils.createExecutorAppInstanceInfo(wm);
        val spec = checkSpec(HTTPVerb.PUT, "Hello");
        try (val checker = new HttpChecker(spec, (HTTPCheckModeSpec) spec.getMode(), info)) {
            val r = checker.call();
            assertEquals(CheckResult.Status.HEALTHY, r.getStatus());
        }
    }

    @Test
    @SneakyThrows
    void testCheckPutFail(WireMockRuntimeInfo wm) {
        stubFor(put("/").willReturn(serverError().withBody("Server Kaput!!")));
        val info = ExecutorTestingUtils.createExecutorAppInstanceInfo(wm);
        val spec = checkSpec(HTTPVerb.PUT);
        try (val checker = new HttpChecker(spec, (HTTPCheckModeSpec) spec.getMode(), info)) {
            val r = checker.call();
            assertEquals(CheckResult.Status.UNHEALTHY, r.getStatus());
            assertTrue(r.getMessage().endsWith("Server Kaput!!"));
        }
    }

    @Test
    @SneakyThrows
    void testCheckPutFailIOException(WireMockRuntimeInfo wm) {
        stubFor(put("/").willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));
        val info = ExecutorTestingUtils.createExecutorAppInstanceInfo(wm);
        val spec = checkSpec(HTTPVerb.PUT);
        try (val checker = new HttpChecker(spec, (HTTPCheckModeSpec) spec.getMode(), info)) {
            val r = checker.call();
            assertEquals(CheckResult.Status.UNHEALTHY, r.getStatus());
            assertTrue(r.getMessage().startsWith("Healthcheck error from "));
        }
    }

    @Test
    @SneakyThrows
    void testCheckPutFailInterruptedException(WireMockRuntimeInfo wm) {
        stubFor(put("/").willReturn(aResponse().withFault(Fault.MALFORMED_RESPONSE_CHUNK)));
        val info = ExecutorTestingUtils.createExecutorAppInstanceInfo(wm);
        val spec = checkSpec(HTTPVerb.PUT);
        try (val checker = new HttpChecker(spec, (HTTPCheckModeSpec) spec.getMode(), info)) {
            val f = Executors.newSingleThreadExecutor()
                    .submit(() -> {
                        Thread.currentThread().interrupt();
                        return checker.call();
                    });
            val r = f.get();
            assertEquals(CheckResult.Status.UNHEALTHY, r.getStatus());
            assertEquals("Healthcheck interrupted", r.getMessage());
        }
    }

    @Test
    @SneakyThrows
    void testMode(WireMockRuntimeInfo wm) {
        val info = ExecutorTestingUtils.createExecutorAppInstanceInfo(wm);
        val spec = checkSpec(HTTPVerb.PUT);
        try (val checker = new HttpChecker(spec, (HTTPCheckModeSpec) spec.getMode(), info)) {
            assertEquals(CheckMode.HTTP, checker.mode());
        }
    }

    @Test
    void testInvalidPort(WireMockRuntimeInfo wm) {

        val info = ExecutorTestingUtils.createExecutorAppInstanceInfo(wm);
        val httpSpec = new HTTPCheckModeSpec(Protocol.HTTP,
                                             "wrongPort",
                                             "/",
                                             HTTPVerb.GET,
                                             Collections.singleton(200),
                                             null,
                                             Duration.seconds(1),
                                             false);
        val spec = checkSpec(httpSpec);
        try (val checker = new HttpChecker(spec, (HTTPCheckModeSpec) spec.getMode(), info)) {

        }
        catch (NullPointerException e) {
            assertEquals("Invalid port spec. No port of name 'wrongPort' exists", e.getMessage());
            return;
        }
        catch (Exception e) {
            //Nothing to do here
        }
        fail("Should have thrown NPE and failed");
    }
}