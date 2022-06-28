package com.phonepe.drove.executor.statemachine.actions;

import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import com.phonepe.drove.common.AbstractTestBase;
import com.phonepe.drove.executor.ExecutorTestingUtils;
import com.phonepe.drove.executor.statemachine.InstanceActionContext;
import com.phonepe.drove.models.instance.InstanceState;
import com.phonepe.drove.statemachine.StateData;
import lombok.SneakyThrows;
import lombok.val;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.Executors;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.phonepe.drove.common.CommonTestUtils.delay;
import static com.phonepe.drove.executor.ExecutorTestingUtils.testSpec;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 *
 */
@WireMockTest
class InstanceHealthcheckActionTest extends AbstractTestBase {

    @Test
    void testUsualFlow(final WireMockRuntimeInfo wm) {
        stubFor(get("/")
                        .inScenario("health-check-test")
                        .whenScenarioStateIs(Scenario.STARTED)
                        .willReturn(ok())
                        .willSetStateTo("unhealthyState"));
        stubFor(get("/")
                        .inScenario("health-check-test")
                        .whenScenarioStateIs("unhealthyState")
                        .willReturn(serverError())
                        .willSetStateTo("unhealthyState"));

        val spec = testSpec("hello-world");
        val ctx = new InstanceActionContext(ExecutorTestingUtils.EXECUTOR_ID, spec, null);
        val action = new InstanceHealthcheckAction();
        val response = action.execute(ctx,
                                      StateData.create(InstanceState.HEALTHY,
                                                       ExecutorTestingUtils.createExecutorInfo(spec, wm)));
        assertEquals(InstanceState.UNHEALTHY, response.getState());
    }

    @Test
    @SneakyThrows
    void testUsualFlowRecovery(final WireMockRuntimeInfo wm) {
        stubFor(get("/")
                        .inScenario("health-check-test")
                        .whenScenarioStateIs(Scenario.STARTED)
                        .willReturn(ok())
                        .willSetStateTo("unhealthyState"));
        stubFor(get("/")
                        .inScenario("health-check-test")
                        .whenScenarioStateIs("unhealthyState")
                        .willReturn(serverError())
                        .willSetStateTo("recoveryState"));
        stubFor(get("/")
                        .inScenario("health-check-test")
                        .whenScenarioStateIs("recoveryState")
                        .willReturn(ok())
                        .willSetStateTo(Scenario.STARTED));

        val spec = testSpec("hello-world", 1);
        val ctx = new InstanceActionContext(ExecutorTestingUtils.EXECUTOR_ID, spec, null);
        val action = new InstanceHealthcheckAction();
        val f = Executors.newSingleThreadExecutor()
                .submit(() -> action.execute(ctx,
                                             StateData.create(InstanceState.HEALTHY,
                                                              ExecutorTestingUtils.createExecutorInfo(spec, wm))));
        delay(Duration.ofSeconds(5));
        action.stop();
        assertEquals(InstanceState.STOPPING, f.get().getState());
    }

    @Test
    void testException(final WireMockRuntimeInfo wm) {
        stubFor(get("/")
                        .inScenario("health-check-test")
                        .whenScenarioStateIs(Scenario.STARTED)
                        .willReturn(ok())
                        .willSetStateTo("unhealthyState"));
        stubFor(get("/")
                        .inScenario("health-check-test")
                        .whenScenarioStateIs("unhealthyState")
                        .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER))
                        .willSetStateTo("unhealthyState"));

        val spec = testSpec("hello-world");
        val ctx = new InstanceActionContext(ExecutorTestingUtils.EXECUTOR_ID, spec, null);
        val action = new InstanceHealthcheckAction();
        val response = action.execute(ctx,
                                      StateData.create(InstanceState.HEALTHY,
                                                       ExecutorTestingUtils.createExecutorInfo(spec, wm)));
        assertEquals(InstanceState.UNHEALTHY, response.getState());
    }

    @SneakyThrows
    @Test
    void testStop(final WireMockRuntimeInfo wm) {
        stubFor(get("/").willReturn(ok()));
        val spec = testSpec("hello-world");
        val ctx = new InstanceActionContext(ExecutorTestingUtils.EXECUTOR_ID, spec, null);
        val action = new InstanceHealthcheckAction();
        val f = Executors.newSingleThreadExecutor()
                .submit(() -> action.execute(ctx,
                                             StateData.create(InstanceState.HEALTHY,
                                                              ExecutorTestingUtils.createExecutorInfo(spec, wm))));
        delay(Duration.ofSeconds(5));
        action.stop();
        assertEquals(InstanceState.STOPPING, f.get().getState());
    }

    @SneakyThrows
    @Test
    void testInterruption(final WireMockRuntimeInfo wm) {
        stubFor(get("/").willReturn(ok()));
        val spec = testSpec("hello-world");
        val ctx = new InstanceActionContext(ExecutorTestingUtils.EXECUTOR_ID, spec, null);
        val action = new InstanceHealthcheckAction();
        val f = Executors.newSingleThreadExecutor()
                .submit(() -> action.execute(ctx,
                                         StateData.create(InstanceState.HEALTHY,
                                                          ExecutorTestingUtils.createExecutorInfo(spec, wm))));
        delay(Duration.ofSeconds(5));
        f.cancel(true);
        assertTrue(f.isCancelled());
    }

    @Test
    @SneakyThrows
    void testRecovery(final WireMockRuntimeInfo wm) {
        stubFor(get("/")
                        .inScenario("health-check-test")
                        .whenScenarioStateIs(Scenario.STARTED)
                        .willReturn(ok())
                        .willSetStateTo("unhealthyState-1"));
        stubFor(get("/")
                        .inScenario("health-check-test")
                        .whenScenarioStateIs("unhealthyState-1")
                        .willReturn(serverError())
                        .willSetStateTo("unhealthyState-2"));
        stubFor(get("/")
                        .inScenario("health-check-test")
                        .whenScenarioStateIs("unhealthyState-2")
                        .willReturn(serverError())
                        .willSetStateTo("healthyState"));
        stubFor(get("/")
                        .inScenario("health-check-test")
                        .whenScenarioStateIs("healthyState")
                        .willReturn(ok())
                        .willSetStateTo("healthyState"));

        val spec = testSpec("hello-world");
        val ctx = new InstanceActionContext(ExecutorTestingUtils.EXECUTOR_ID, spec, null);
        val action = new InstanceHealthcheckAction();
        val f = Executors.newSingleThreadExecutor()
                .submit(() -> action.execute(ctx,
                                             StateData.create(InstanceState.HEALTHY,
                                                              ExecutorTestingUtils.createExecutorInfo(spec, wm))));
        val healthcheck = spec.getHealthcheck();
        val totalDelay = (healthcheck.getAttempts() * healthcheck.getInterval().toMilliseconds())
                + healthcheck.getInitialDelay().toMilliseconds();
        delay(Duration.ofMillis(totalDelay));
        action.stop();
        assertEquals(InstanceState.STOPPING, f.get().getState());
    }

}