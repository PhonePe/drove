package com.phonepe.drove.executor.statemachine.actions;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import com.phonepe.drove.common.StateData;
import com.phonepe.drove.executor.ExecutorTestingUtils;
import com.phonepe.drove.executor.statemachine.InstanceActionContext;
import com.phonepe.drove.models.instance.InstanceState;
import lombok.SneakyThrows;
import lombok.val;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.Executors;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.phonepe.drove.common.CommonTestUtils.delay;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 *
 */
@WireMockTest
class InstanceReadinessCheckActionTest {

    @Test
    void testUsualFlow(final WireMockRuntimeInfo wm) {
        stubFor(get("/")
                        .inScenario("readycheck")
                        .whenScenarioStateIs(Scenario.STARTED)
                        .willReturn(serverError())
                        .willSetStateTo("unhealthyState"));
        stubFor(get("/")
                        .inScenario("readycheck")
                        .whenScenarioStateIs("unhealthyState")
                        .willReturn(serverError())
                        .willSetStateTo("healthyState"));
        stubFor(get("/")
                        .inScenario("readycheck")
                        .whenScenarioStateIs("healthyState")
                        .willReturn(ok())
                        .willSetStateTo("healthyState"));

        val ctx = new InstanceActionContext("EX1", ExecutorTestingUtils.testSpec("hello-world"), null);
        val action = new InstanceReadinessCheckAction();
        val response = action.execute(ctx,
                                      StateData.create(InstanceState.HEALTHY,
                                                       ExecutorTestingUtils.createExecutorInfo(wm.getHttpPort())));
        assertEquals(InstanceState.READY, response.getState());
    }

    @Test
    void testFail(final WireMockRuntimeInfo wm) {
        stubFor(get("/").willReturn(serverError()));

        val ctx = new InstanceActionContext("EX1", ExecutorTestingUtils.testSpec("hello-world"), null);
        val action = new InstanceReadinessCheckAction();
        val response = action.execute(ctx,
                                      StateData.create(InstanceState.HEALTHY,
                                                       ExecutorTestingUtils.createExecutorInfo(wm.getHttpPort())));
        assertEquals(InstanceState.READINESS_CHECK_FAILED, response.getState());
    }

    @SneakyThrows
    @Test
    void testStop(final WireMockRuntimeInfo wm) {
        stubFor(get("/").willReturn(serverError()));

        val ctx = new InstanceActionContext("EX1", ExecutorTestingUtils.testSpec("hello-world"), null);
        val action = new InstanceReadinessCheckAction();
        val f = Executors.newSingleThreadExecutor()
                .submit(() -> action.execute(ctx,
                                             StateData.create(InstanceState.HEALTHY,
                                                              ExecutorTestingUtils.createExecutorInfo(wm.getHttpPort()))));
        delay(Duration.ofSeconds(5));
        action.stop();
        assertEquals(InstanceState.STOPPING, f.get().getState());
    }

    @Test
    void testInterrupt(final WireMockRuntimeInfo wm) {
        stubFor(get("/").willReturn(serverError()));
        val ctx = new InstanceActionContext("EX1", ExecutorTestingUtils.testSpec("hello-world"), null);
        val action = new InstanceReadinessCheckAction();
        val f = Executors.newSingleThreadExecutor()
                .submit(() -> action.execute(ctx,
                                             StateData.create(InstanceState.HEALTHY,
                                                              ExecutorTestingUtils.createExecutorInfo(wm.getHttpPort()))));
        f.cancel(true);
        assertTrue(f.isCancelled());
    }
}