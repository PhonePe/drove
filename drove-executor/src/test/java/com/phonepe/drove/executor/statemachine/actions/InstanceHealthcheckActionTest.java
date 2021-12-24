package com.phonepe.drove.executor.statemachine.actions;

import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import com.phonepe.drove.common.AbstractTestBase;
import com.phonepe.drove.common.CommonTestUtils;
import com.phonepe.drove.common.StateData;
import com.phonepe.drove.executor.ExecutorTestingUtils;
import com.phonepe.drove.executor.statemachine.InstanceActionContext;
import com.phonepe.drove.models.instance.InstanceState;
import lombok.SneakyThrows;
import lombok.val;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.concurrent.Executors;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
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
                        .inScenario("healthchecktest")
                        .whenScenarioStateIs(Scenario.STARTED)
                        .willReturn(ok())
                        .willSetStateTo("unhealthyState"));
        stubFor(get("/")
                        .inScenario("healthchecktest")
                        .whenScenarioStateIs("unhealthyState")
                        .willReturn(serverError())
                        .willSetStateTo("unhealthyState"));
        ;

        val ctx = new InstanceActionContext("EX1", ExecutorTestingUtils.testSpec("hello-world"), null);
        val action = new InstanceHealthcheckAction();
        val response = action.execute(ctx,
                                      StateData.create(InstanceState.HEALTHY,
                                                       ExecutorTestingUtils.createExecutorInfo(wm.getHttpPort())));
        assertEquals(InstanceState.UNHEALTHY, response.getState());
    }

    @Test
    void testException(final WireMockRuntimeInfo wm) {
        stubFor(get("/")
                        .inScenario("healthchecktest")
                        .whenScenarioStateIs(Scenario.STARTED)
                        .willReturn(ok())
                        .willSetStateTo("unhealthyState"));
        stubFor(get("/")
                        .inScenario("healthchecktest")
                        .whenScenarioStateIs("unhealthyState")
                        .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER))
                        .willSetStateTo("unhealthyState"));
        ;

        val ctx = new InstanceActionContext("EX1", ExecutorTestingUtils.testSpec("hello-world"), null);
        val action = new InstanceHealthcheckAction();
        val response = action.execute(ctx,
                                      StateData.create(InstanceState.HEALTHY,
                                                       ExecutorTestingUtils.createExecutorInfo(wm.getHttpPort())));
        assertEquals(InstanceState.UNHEALTHY, response.getState());
    }

    @SneakyThrows
    @Test
    void testStop(final WireMockRuntimeInfo wm) {
        stubFor(get("/").willReturn(ok()));
        val ctx = new InstanceActionContext("EX1", ExecutorTestingUtils.testSpec("hello-world"), null);
        val action = new InstanceHealthcheckAction();
        val f = Executors.newSingleThreadExecutor()
                .submit(() -> action.execute(ctx,
                                             StateData.create(InstanceState.HEALTHY,
                                                              ExecutorTestingUtils.createExecutorInfo(wm.getHttpPort()))));
        val endTime = new Date(new Date().getTime() + 5_000);
        CommonTestUtils.delay(endTime);
        action.stop();
        assertEquals(InstanceState.STOPPING, f.get().getState());
    }

    @SneakyThrows
    @Test
    void testInterruption(final WireMockRuntimeInfo wm) {
        stubFor(get("/").willReturn(ok()));
        val ctx = new InstanceActionContext("EX1", ExecutorTestingUtils.testSpec("hello-world"), null);
        val action = new InstanceHealthcheckAction();
        val f = Executors.newSingleThreadExecutor()
                .submit(() -> {
                    return action.execute(ctx,
                                          StateData.create(InstanceState.HEALTHY,
                                                           ExecutorTestingUtils.createExecutorInfo(wm.getHttpPort())));

                });
        val endTime = new Date(new Date().getTime() + 5_000);
        CommonTestUtils.delay(endTime);
        f.cancel(true);
        assertTrue(f.isCancelled());
    }

    @Test
    @SneakyThrows
    void testRecovery(final WireMockRuntimeInfo wm) {
        stubFor(get("/")
                        .inScenario("healthchecktest")
                        .whenScenarioStateIs(Scenario.STARTED)
                        .willReturn(ok())
                        .willSetStateTo("unhealthyState-1"));
        stubFor(get("/")
                        .inScenario("healthchecktest")
                        .whenScenarioStateIs("unhealthyState-1")
                        .willReturn(serverError())
                        .willSetStateTo("unhealthyState-2"));
        stubFor(get("/")
                        .inScenario("healthchecktest")
                        .whenScenarioStateIs("unhealthyState-2")
                        .willReturn(serverError())
                        .willSetStateTo("healthyState"));
        stubFor(get("/")
                        .inScenario("healthchecktest")
                        .whenScenarioStateIs("healthyState")
                        .willReturn(ok())
                        .willSetStateTo("healthyState"));
        ;

        val spec = ExecutorTestingUtils.testSpec("hello-world");
        val ctx = new InstanceActionContext("EX1", spec, null);
        val action = new InstanceHealthcheckAction();
        val f = Executors.newSingleThreadExecutor()
                .submit(() -> action.execute(ctx,
                                             StateData.create(InstanceState.HEALTHY,
                                                              ExecutorTestingUtils.createExecutorInfo(wm.getHttpPort()))));
        val healthcheck = spec.getHealthcheck();
        val totalDelay = (healthcheck.getAttempts() * healthcheck.getInterval().toMilliseconds())
                + healthcheck.getInitialDelay().toMilliseconds();
        val endTime = new Date(new Date().getTime() + totalDelay + 5_000);
        CommonTestUtils.delay(endTime);
        action.stop();
        assertEquals(InstanceState.STOPPING, f.get().getState());
    }

}