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

package com.phonepe.drove.controller.managed;

import com.phonepe.drove.controller.ControllerTestBase;
import com.phonepe.drove.controller.ControllerTestUtils;
import com.phonepe.drove.controller.engine.ApplicationLifecycleManagementEngine;
import com.phonepe.drove.controller.engine.LocalServiceLifecycleManagementEngine;
import com.phonepe.drove.controller.engine.TaskEngine;
import com.phonepe.drove.controller.engine.ValidationResult;
import com.phonepe.drove.controller.statedb.ApplicationStateDB;
import com.phonepe.drove.controller.statedb.LocalServiceStateDB;
import com.phonepe.drove.controller.statedb.TaskDB;
import com.phonepe.drove.controller.utils.ControllerUtils;
import com.phonepe.drove.models.application.ApplicationInfo;
import com.phonepe.drove.models.localservice.ActivationState;
import com.phonepe.drove.models.localservice.LocalServiceInfo;
import com.phonepe.drove.models.operation.ApplicationOperation;
import com.phonepe.drove.models.operation.LocalServiceOperation;
import io.appform.signals.signals.ConsumingSyncSignal;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.Date;
import java.util.HashSet;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.phonepe.drove.controller.utils.ControllerUtils.deployableObjectId;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests {@link AppRecovery}
 */
class AppRecoveryTest extends ControllerTestBase {

    @Test
    void testRecovery() {
        val le = mock(LeadershipEnsurer.class);
        val ae = mock(ApplicationLifecycleManagementEngine.class);
        val te = mock(TaskEngine.class);
        val asdb = mock(ApplicationStateDB.class);
        val tdb = mock(TaskDB.class);
        val lse = mock(LocalServiceLifecycleManagementEngine.class);
        val lsdb = mock(LocalServiceStateDB.class);

        val lsc = new ConsumingSyncSignal<Boolean>();
        when(le.onLeadershipStateChanged()).thenReturn(lsc);

        val ar = new AppRecovery(le, ae, te, lse, asdb, tdb, lsdb, ControllerTestUtils.DEFAULT_CLUSTER_OP);

        val specs = IntStream.rangeClosed(1, 100)
                .mapToObj(ControllerTestUtils::appSpec)
                .map(spec -> new ApplicationInfo(ControllerUtils.deployableObjectId(spec),
                                                 spec,
                                                 10,
                                                 new Date(),
                                                 new Date()))
                .toList();
        val serviceSpecs = IntStream.rangeClosed(1, 100)
                .mapToObj(ControllerTestUtils::localServiceSpec)
                .map(spec -> new LocalServiceInfo(ControllerUtils.deployableObjectId(spec),
                                                  spec,
                                                  1,
                                                  ActivationState.ACTIVE,
                                                  new Date(),
                                                  new Date()))
                .toList();
        when(asdb.applications(0, Integer.MAX_VALUE)).thenReturn(specs);
        when(tdb.tasks(any(), any(), anyBoolean())).thenReturn(Map.of());
        when(lsdb.services(0, Integer.MAX_VALUE)).thenReturn(serviceSpecs);
        val appIds = new HashSet<String>();
        when(ae.handleOperation(any(ApplicationOperation.class)))
                .thenAnswer(new Answer<ValidationResult>() {
                    @Override
                    public ValidationResult answer(InvocationOnMock invocationOnMock) throws Throwable {
                        appIds.add(deployableObjectId(invocationOnMock.getArgument(0, ApplicationOperation.class)));
                        return ValidationResult.success();
                    }
                });
        val serviceIds = new HashSet<String>();
        when(lse.handleOperation(any(LocalServiceOperation.class)))
                .thenAnswer(new Answer<ValidationResult>() {
                    @Override
                    public ValidationResult answer(InvocationOnMock invocationOnMock) throws Throwable {
                        serviceIds.add(deployableObjectId(invocationOnMock.getArgument(0, LocalServiceOperation.class)));
                        return ValidationResult.success();
                    }
                });
        lsc.dispatch(true);
        assertEquals(specs.stream().map(ApplicationInfo::getAppId).collect(Collectors.toSet()), appIds);
        assertEquals(serviceSpecs.stream().map(LocalServiceInfo::getServiceId).collect(Collectors.toSet()), serviceIds);
        ar.stop();
    }

    @Test
    void testNoRecoveryNotLeader() {
        val le = mock(LeadershipEnsurer.class);
        val ae = mock(ApplicationLifecycleManagementEngine.class);
        val te = mock(TaskEngine.class);
        val asdb = mock(ApplicationStateDB.class);
        val tdb = mock(TaskDB.class);
        val lse = mock(LocalServiceLifecycleManagementEngine.class);
        val lsdb = mock(LocalServiceStateDB.class);

        val lsc = new ConsumingSyncSignal<Boolean>();
        when(le.onLeadershipStateChanged()).thenReturn(lsc);

        val ar = new AppRecovery(le, ae, te, lse, asdb, tdb, lsdb, ControllerTestUtils.DEFAULT_CLUSTER_OP);

        val specs = IntStream.rangeClosed(1, 100)
                .mapToObj(ControllerTestUtils::appSpec)
                .map(spec -> new ApplicationInfo(ControllerUtils.deployableObjectId(spec),
                                                 spec,
                                                 10,
                                                 new Date(),
                                                 new Date()))
                .toList();
        val serviceSpecs = IntStream.rangeClosed(1, 100)
                .mapToObj(ControllerTestUtils::localServiceSpec)
                .map(spec -> new LocalServiceInfo(ControllerUtils.deployableObjectId(spec),
                                                  spec,
                                                  1,
                                                  ActivationState.ACTIVE,
                                                  new Date(),
                                                  new Date()))
                .toList();
        when(asdb.applications(0, Integer.MAX_VALUE)).thenReturn(specs);
        when(tdb.tasks(any(), any(), anyBoolean())).thenReturn(Map.of());
        when(lsdb.services(0, Integer.MAX_VALUE)).thenReturn(serviceSpecs);
        when(ae.handleOperation(any(ApplicationOperation.class)))
                .thenThrow(new IllegalStateException("Should not have been called"));
        when(lse.handleOperation(any(LocalServiceOperation.class)))
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