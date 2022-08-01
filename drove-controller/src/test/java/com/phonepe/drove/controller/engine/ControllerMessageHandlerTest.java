package com.phonepe.drove.controller.engine;

import com.phonepe.drove.common.model.MessageDeliveryStatus;
import com.phonepe.drove.common.model.MessageHeader;
import com.phonepe.drove.common.model.controller.ExecutorSnapshotMessage;
import com.phonepe.drove.common.model.controller.InstanceStateReportMessage;
import com.phonepe.drove.controller.event.DroveEvent;
import com.phonepe.drove.controller.event.DroveEventBus;
import com.phonepe.drove.controller.utils.ControllerUtils;
import com.phonepe.drove.models.instance.InstanceInfo;
import com.phonepe.drove.models.instance.InstanceState;
import lombok.val;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.concurrent.atomic.AtomicInteger;

import static com.phonepe.drove.controller.ControllerTestUtils.*;
import static com.phonepe.drove.controller.utils.ControllerUtils.deployableObjectId;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

/**
 *
 */
class ControllerMessageHandlerTest {

    @Test
    void testInstanceStateReportMessageSuccess() {
        val ev = mock(DroveEventBus.class);
        val su = mock(StateUpdater.class);
        val cmh = new ControllerMessageHandler(su, ev);

        val spec = appSpec();
        val aid = ControllerUtils.deployableObjectId(spec);
        val instance = generateInstanceInfo(aid, spec, 1, InstanceState.HEALTHY, new Date(), "Test Error");
        val ctr = new AtomicInteger();
        doAnswer(invocationOnMock -> {
            ctr.incrementAndGet();
            return true;
        }).when(su).updateSingle(isNull(), any(InstanceInfo.class));

        doAnswer(invocationOnMock -> {
            ctr.incrementAndGet();
            return null;
        }).when(ev).publish(any(DroveEvent.class));

        val r = new InstanceStateReportMessage(MessageHeader.executorRequest(), null, instance)
                .accept(cmh);
        assertEquals(2, ctr.get());
        assertEquals(MessageDeliveryStatus.ACCEPTED, r.getStatus());
    }

    @Test
    void testInstanceStateReportMessageFailure() {
        val ev = mock(DroveEventBus.class);
        val su = mock(StateUpdater.class);
        val cmh = new ControllerMessageHandler(su, ev);

        val spec = appSpec();
        val aid = ControllerUtils.deployableObjectId(spec);
        val instance = generateInstanceInfo(aid, spec, 1, InstanceState.HEALTHY, new Date(), "Test Error");
        val ctr = new AtomicInteger();
        doAnswer(invocationOnMock -> {
            ctr.incrementAndGet();
            return false;
        }).when(su).updateSingle(isNull(), any(InstanceInfo.class));

        doAnswer(invocationOnMock -> {
            ctr.incrementAndGet();
            return null;
        }).when(ev).publish(any(DroveEvent.class));

        val r = new InstanceStateReportMessage(MessageHeader.executorRequest(), null, instance)
                .accept(cmh);
        assertEquals(2, ctr.get());
        assertEquals(MessageDeliveryStatus.FAILED, r.getStatus());
    }

    @Test
    void testExecutorSnapshotMessage() {
        val ev = mock(DroveEventBus.class);
        val su = mock(StateUpdater.class);
        val cmh = new ControllerMessageHandler(su, ev);
        val ctr = new AtomicInteger();
        doAnswer(invocationOnMock -> {
            ctr.incrementAndGet();
            return true;
        }).when(su).updateClusterResources(anyList());

        val r = new ExecutorSnapshotMessage(MessageHeader.executorRequest(), generateExecutorNode(0))
                .accept(cmh);
        assertEquals(1, ctr.get());
        assertEquals(MessageDeliveryStatus.ACCEPTED, r.getStatus());
    }
}