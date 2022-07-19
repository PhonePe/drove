package com.phonepe.drove.executor.statemachine.application.actions;

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

import java.util.concurrent.Executors;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.phonepe.drove.executor.ExecutorTestingUtils.testSpec;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 *
 */
@WireMockTest
class ApplicationInstanceSingularHealthCheckActionTest extends AbstractTestBase {

    @Test
    void testUsualFlow(final WireMockRuntimeInfo wm) {
        stubFor(get("/").willReturn(ok()));

        val spec = testSpec("hello-world");
        val ctx = new InstanceActionContext(ExecutorTestingUtils.EXECUTOR_ID, spec, null);
        val action = new ApplicationInstanceSingularHealthCheckAction();
        val response = action.execute(ctx,
                                      StateData.create(InstanceState.HEALTHY,
                                                       ExecutorTestingUtils.createExecutorInfo(spec, wm)));
        assertEquals(InstanceState.HEALTHY, response.getState());
    }


    @Test
    void testFailSucceed(final WireMockRuntimeInfo wm) {
        stubFor(get("/")
                        .inScenario("health-check-test")
                        .whenScenarioStateIs(Scenario.STARTED)
                        .willReturn(serverError())
                        .willSetStateTo("healthyState"));
        stubFor(get("/")
                        .inScenario("health-check-test")
                        .whenScenarioStateIs("healthyState")
                        .willReturn(ok()));
        val spec = testSpec("hello-world");
        val ctx = new InstanceActionContext(ExecutorTestingUtils.EXECUTOR_ID, spec, null);
        val action = new ApplicationInstanceSingularHealthCheckAction();
        val response = action.execute(ctx,
                                      StateData.create(InstanceState.HEALTHY,
                                                       ExecutorTestingUtils.createExecutorInfo(spec, wm)));
        assertEquals(InstanceState.HEALTHY, response.getState());
    }

    @Test
    void testFail(final WireMockRuntimeInfo wm) {
        stubFor(get("/").willReturn(serverError()));

        val spec = testSpec("hello-world");
        val ctx = new InstanceActionContext(ExecutorTestingUtils.EXECUTOR_ID, spec, null);
        val action = new ApplicationInstanceSingularHealthCheckAction();
        val response = action.execute(ctx,
                                      StateData.create(InstanceState.HEALTHY,
                                                       ExecutorTestingUtils.createExecutorInfo(spec, wm)));
        assertEquals(InstanceState.STOPPING, response.getState());
    }

    @Test
    void testException(final WireMockRuntimeInfo wm) {
        stubFor(get("/").willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

        val spec = testSpec("hello-world");
        val ctx = new InstanceActionContext(ExecutorTestingUtils.EXECUTOR_ID, spec, null);
        val action = new ApplicationInstanceSingularHealthCheckAction();
        val response = action.execute(ctx,
                                      StateData.create(InstanceState.HEALTHY,
                                                       ExecutorTestingUtils.createExecutorInfo(spec, wm)));
        assertEquals(InstanceState.STOPPING, response.getState());
    }

    @Test
    @SneakyThrows
    void testStop(final WireMockRuntimeInfo wm) {
        stubFor(get("/").willReturn(serverError()));

        val spec = testSpec("hello-world");
        val ctx = new InstanceActionContext(ExecutorTestingUtils.EXECUTOR_ID, spec, null);
        val action = new ApplicationInstanceSingularHealthCheckAction();
        val f = Executors.newSingleThreadExecutor()
                .submit(() -> action.execute(ctx,
                                         StateData.create(InstanceState.HEALTHY,
                                                       ExecutorTestingUtils.createExecutorInfo(spec, wm))));
        action.stop();
        assertEquals(InstanceState.STOPPING, f.get().getState());
    }
}