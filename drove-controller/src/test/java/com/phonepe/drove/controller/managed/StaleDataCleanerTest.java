package com.phonepe.drove.controller.managed;

import com.phonepe.drove.controller.ControllerTestUtils;
import com.phonepe.drove.controller.engine.ApplicationEngine;
import com.phonepe.drove.controller.engine.CommandValidator;
import com.phonepe.drove.controller.testsupport.InMemoryApplicationStateDB;
import com.phonepe.drove.controller.testsupport.InMemoryInstanceInfoDB;
import com.phonepe.drove.models.application.ApplicationInfo;
import com.phonepe.drove.models.instance.InstanceState;
import com.phonepe.drove.models.operation.ops.ApplicationDestroyOperation;
import lombok.val;
import org.junit.jupiter.api.Test;

import java.sql.Date;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.IntStream;

import static com.phonepe.drove.controller.ControllerTestUtils.appSpec;
import static com.phonepe.drove.controller.utils.ControllerUtils.appId;
import static com.phonepe.drove.models.application.ApplicationState.MONITORING;
import static com.phonepe.drove.models.application.ApplicationState.RUNNING;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 *
 */
class StaleDataCleanerTest {

    @Test
    void testStaleAppCleanup() {
        val appStateDB = new InMemoryApplicationStateDB();
        val instanceDB = new InMemoryInstanceInfoDB();
        val le = mock(LeadershipEnsurer.class);
        when(le.isLeader()).thenReturn(true);
        val engine = mock(ApplicationEngine.class);

        val sdc = new StaleDataCleaner(appStateDB, instanceDB, le, engine, Duration.ofSeconds(1));

        val spec = appSpec();
        val appId = appId(spec);
        val oldDate = Date.from(LocalDate.now().minusDays(32).atStartOfDay(ZoneId.systemDefault()).toInstant());
        appStateDB.updateApplicationState(appId, new ApplicationInfo(appId, spec, 0, oldDate, oldDate));

        val testRun = new AtomicBoolean();
        when(engine.applicationState(anyString())).thenReturn(Optional.of(MONITORING));
        when(engine.handleOperation(any(ApplicationDestroyOperation.class)))
                .thenAnswer(invocationOnMock -> {
                    val dId = invocationOnMock.getArgument(0, ApplicationDestroyOperation.class).getAppId();
                    testRun.set(dId.equals(appId));
                    appStateDB.deleteApplicationState(appId);
                    return CommandValidator.ValidationResult.success();
                });
        sdc.start();
        await().atMost(Duration.ofMinutes(1))
                        .until(testRun::get);
        sdc.stop();
        assertNull(appStateDB.application(appId).orElse(null));
    }

    @Test
    void testStaleInstanceCleanup() {
        val appStateDB = new InMemoryApplicationStateDB();
        val instanceDB = new InMemoryInstanceInfoDB();
        val le = mock(LeadershipEnsurer.class);
        when(le.isLeader()).thenReturn(true);
        val engine = mock(ApplicationEngine.class);

        val sdc = new StaleDataCleaner(appStateDB, instanceDB, le, engine, Duration.ofSeconds(1));

        val spec = appSpec();
        val appId = appId(spec);
        val oldDate = Date.from(LocalDate.now().minusDays(32).atStartOfDay(ZoneId.systemDefault()).toInstant());
        appStateDB.updateApplicationState(appId, new ApplicationInfo(appId, spec, 0, oldDate, oldDate));
        IntStream.rangeClosed(1, 100)
                .forEach(i -> {
                    val instance = ControllerTestUtils.generateInstanceInfo(appId, spec, i, InstanceState.STOPPED, oldDate);
                    instanceDB.updateInstanceState(appId, instance.getInstanceId(), instance);
                });
        when(engine.applicationState(anyString())).thenReturn(Optional.of(RUNNING));

        sdc.start();
        await().atMost(Duration.ofMinutes(1)).until(() -> instanceDB.oldInstances(appId, 0, Integer.MAX_VALUE).isEmpty());
        sdc.stop();

    }

}