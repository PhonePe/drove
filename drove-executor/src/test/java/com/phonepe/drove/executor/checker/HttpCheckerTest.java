package com.phonepe.drove.executor.checker;

import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.phonepe.drove.models.application.CheckResult;
import com.phonepe.drove.models.application.checks.CheckMode;
import com.phonepe.drove.models.application.checks.HTTPCheckModeSpec;
import com.phonepe.drove.models.common.HTTPVerb;
import io.dropwizard.util.Duration;
import lombok.SneakyThrows;
import lombok.val;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.concurrent.Executors;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.phonepe.drove.executor.ExecutorTestingUtils.checkSpec;
import static com.phonepe.drove.executor.ExecutorTestingUtils.createExecutorInfo;
import static org.junit.jupiter.api.Assertions.*;

@WireMockTest
class HttpCheckerTest {

    @Test
    void testCheckGetSuccess(WireMockRuntimeInfo wm) {
        stubFor(get("/").willReturn(ok()));
        val info = createExecutorInfo(wm);
        val spec = checkSpec(HTTPVerb.GET);
        val checker = new HttpChecker(spec,
                                      (HTTPCheckModeSpec) spec.getMode(),
                                      info);
        val r = checker.call();
        assertEquals(CheckResult.Status.HEALTHY, r.getStatus());
    }

    @Test
    void testCheckGetFail(WireMockRuntimeInfo wm) {
        stubFor(get("/").willReturn(serverError().withBody("Server Kaput!!")));
        val info = createExecutorInfo(wm);
        val spec = checkSpec(HTTPVerb.GET);
        val checker = new HttpChecker(spec,
                                      (HTTPCheckModeSpec) spec.getMode(),
                                      info);
        val r = checker.call();
        assertEquals(CheckResult.Status.UNHEALTHY, r.getStatus());
        assertTrue(r.getMessage().endsWith("Server Kaput!!"));
    }

    @Test
    void testCheckPostSuccess(WireMockRuntimeInfo wm) {
        stubFor(post("/").willReturn(ok()));
        val info = createExecutorInfo(wm);
        val spec = checkSpec(HTTPVerb.POST);
        val checker = new HttpChecker(spec,
                                      (HTTPCheckModeSpec) spec.getMode(),
                                      info);
        val r = checker.call();
        assertEquals(CheckResult.Status.HEALTHY, r.getStatus());
    }

    @Test
    void testCheckPostSuccessWithBody(WireMockRuntimeInfo wm) {
        stubFor(post("/").withRequestBody(equalTo("Hello")).willReturn(ok()));
        val info = createExecutorInfo(wm);
        val spec = checkSpec(HTTPVerb.POST, "Hello");
        val checker = new HttpChecker(spec,
                                      (HTTPCheckModeSpec) spec.getMode(),
                                      info);
        val r = checker.call();
        assertEquals(CheckResult.Status.HEALTHY, r.getStatus());
    }

    @Test
    void testCheckPostFail(WireMockRuntimeInfo wm) {
        stubFor(post("/").willReturn(serverError().withBody("Server Kaput!!")));
        val info = createExecutorInfo(wm);
        val spec = checkSpec(HTTPVerb.POST);
        val checker = new HttpChecker(spec,
                                      (HTTPCheckModeSpec) spec.getMode(),
                                      info);
        val r = checker.call();
        assertEquals(CheckResult.Status.UNHEALTHY, r.getStatus());
        assertTrue(r.getMessage().endsWith("Server Kaput!!"));
    }

    @Test
    void testCheckPutSuccess(WireMockRuntimeInfo wm) {
        stubFor(put("/").willReturn(ok()));
        val info = createExecutorInfo(wm);
        val spec = checkSpec(HTTPVerb.PUT);
        val checker = new HttpChecker(spec,
                                      (HTTPCheckModeSpec) spec.getMode(),
                                      info);
        val r = checker.call();
        assertEquals(CheckResult.Status.HEALTHY, r.getStatus());
    }

    @Test
    void testCheckPutSuccessWithBody(WireMockRuntimeInfo wm) {
        stubFor(put("/").withRequestBody(equalTo("Hello")).willReturn(ok()));
        val info = createExecutorInfo(wm);
        val spec = checkSpec(HTTPVerb.PUT, "Hello");
        val checker = new HttpChecker(spec,
                                      (HTTPCheckModeSpec) spec.getMode(),
                                      info);
        val r = checker.call();
        assertEquals(CheckResult.Status.HEALTHY, r.getStatus());
    }

    @Test
    void testCheckPutFail(WireMockRuntimeInfo wm) {
        stubFor(put("/").willReturn(serverError().withBody("Server Kaput!!")));
        val info = createExecutorInfo(wm);
        val spec = checkSpec(HTTPVerb.PUT);
        val checker = new HttpChecker(spec,
                                      (HTTPCheckModeSpec) spec.getMode(),
                                      info);
        val r = checker.call();
        assertEquals(CheckResult.Status.UNHEALTHY, r.getStatus());
        assertTrue(r.getMessage().endsWith("Server Kaput!!"));
    }

    @Test
    void testCheckPutFailIOException(WireMockRuntimeInfo wm) {
        stubFor(put("/").willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));
        val info = createExecutorInfo(wm);
        val spec = checkSpec(HTTPVerb.PUT);
        val checker = new HttpChecker(spec,
                                      (HTTPCheckModeSpec) spec.getMode(),
                                      info);
        val r = checker.call();
        assertEquals(CheckResult.Status.UNHEALTHY, r.getStatus());
        assertTrue(r.getMessage().startsWith("Healthcheck error from "));
    }

    @Test
    @SneakyThrows
    void testCheckPutFailInterruptedException(WireMockRuntimeInfo wm) {
        stubFor(put("/").willReturn(aResponse().withFault(Fault.MALFORMED_RESPONSE_CHUNK)));
        val info = createExecutorInfo(wm);
        val spec = checkSpec(HTTPVerb.PUT);
        val checker = new HttpChecker(spec,
                                      (HTTPCheckModeSpec) spec.getMode(),
                                      info);
        val f = Executors.newSingleThreadExecutor()
                .submit(() -> {
                    Thread.currentThread().interrupt();
                    return checker.call();
                });
        val r = f.get();
        assertEquals(CheckResult.Status.UNHEALTHY, r.getStatus());
        assertEquals("Healthcheck interrupted", r.getMessage());
    }

    @Test
    void testMode(WireMockRuntimeInfo wm) {
        val info = createExecutorInfo(wm);
        val spec = checkSpec(HTTPVerb.PUT);
        val checker = new HttpChecker(spec,
                                      (HTTPCheckModeSpec) spec.getMode(),
                                      info);
        assertEquals(CheckMode.HTTP, checker.mode());
    }

    @Test
    void testInvalidPort(WireMockRuntimeInfo wm) {

        val info = createExecutorInfo(wm);
        val httpSpec = new HTTPCheckModeSpec("http",
                                             "wrongPort",
                                             "/",
                                             HTTPVerb.GET,
                                             Collections.singleton(200),
                                             null,
                                             Duration.seconds(1));
        val spec = checkSpec(httpSpec);
        try {
            new HttpChecker(spec, (HTTPCheckModeSpec) spec.getMode(), info);
        }
        catch (NullPointerException e) {
            assertEquals("Invalid port spec. No port of name 'wrongPort' exists", e.getMessage());
            return;
        }
        fail("Should have thrown NPE and failed");
    }
}