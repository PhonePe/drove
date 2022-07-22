package com.phonepe.drove.controller.managed;

import com.phonepe.drove.controller.ControllerTestBase;
import com.phonepe.drove.controller.ControllerTestUtils;
import com.phonepe.drove.controller.engine.ApplicationEngine;
import com.phonepe.drove.controller.engine.CommandValidator;
import com.phonepe.drove.controller.statedb.ApplicationStateDB;
import com.phonepe.drove.controller.utils.ControllerUtils;
import com.phonepe.drove.models.application.ApplicationInfo;
import com.phonepe.drove.models.operation.ApplicationOperation;
import io.appform.signals.signals.ConsumingSyncSignal;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.Date;
import java.util.HashSet;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.phonepe.drove.controller.utils.ControllerUtils.deployableObjectId;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 *
 */
class AppRecoveryTest extends ControllerTestBase {

    @Test
    void testRecovery() {
        val le = mock(LeadershipEnsurer.class);
        val ae = mock(ApplicationEngine.class);
        val asdb = mock(ApplicationStateDB.class);


        val lsc = new ConsumingSyncSignal<Boolean>();
        when(le.onLeadershipStateChanged()).thenReturn(lsc);

        val ar = new AppRecovery(le, ae, asdb);

        val specs = IntStream.rangeClosed(1, 100)
                .mapToObj(ControllerTestUtils::appSpec)
                .map(spec -> new ApplicationInfo(ControllerUtils.deployableObjectId(spec), spec, 10, new Date(), new Date()))
                .toList();
        when(asdb.applications(0, Integer.MAX_VALUE)).thenReturn(specs);
        val ids = new HashSet<String>();
        when(ae.handleOperation(any(ApplicationOperation.class)))
                .thenAnswer(new Answer<CommandValidator.ValidationResult>() {
                    @Override
                    public CommandValidator.ValidationResult answer(InvocationOnMock invocationOnMock) throws Throwable {
                        ids.add(deployableObjectId(invocationOnMock.getArgument(0, ApplicationOperation.class)));
                        return CommandValidator.ValidationResult.success();
                    }
                });
        lsc.dispatch(true);
        assertEquals(specs.stream().map(ApplicationInfo::getAppId).collect(Collectors.toSet()), ids);
        ar.stop();
    }

    @Test
    void testNoRecovery() {
        val le = mock(LeadershipEnsurer.class);
        val ae = mock(ApplicationEngine.class);
        val asdb = mock(ApplicationStateDB.class);


        val lsc = new ConsumingSyncSignal<Boolean>();
        when(le.onLeadershipStateChanged()).thenReturn(lsc);

        val ar = new AppRecovery(le, ae, asdb);

        val specs = IntStream.rangeClosed(1, 100)
                .mapToObj(ControllerTestUtils::appSpec)
                .map(spec -> new ApplicationInfo(ControllerUtils.deployableObjectId(spec), spec, 10, new Date(), new Date()))
                .toList();
        when(asdb.applications(0, Integer.MAX_VALUE)).thenReturn(specs);
        when(ae.handleOperation(any(ApplicationOperation.class)))
                .thenThrow(new IllegalStateException("Should not have been called"));
        try {
            lsc.dispatch(false);
        }
        catch (IllegalStateException e) {
            fail();
        }

        ar.stop();
    }
}