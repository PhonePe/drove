package com.phonepe.drove.executor.engine;

import com.phonepe.drove.common.model.ApplicationInstanceSpec;
import com.phonepe.drove.common.model.MessageDeliveryStatus;
import com.phonepe.drove.common.model.MessageHeader;
import com.phonepe.drove.common.model.executor.BlacklistExecutorMessage;
import com.phonepe.drove.common.model.executor.StartInstanceMessage;
import com.phonepe.drove.common.model.executor.StopInstanceMessage;
import com.phonepe.drove.common.model.executor.UnBlacklistExecutorMessage;
import lombok.val;
import org.junit.jupiter.api.Test;

import static com.phonepe.drove.common.CommonTestUtils.executor;
import static com.phonepe.drove.executor.ExecutorTestingUtils.testSpec;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 *
 */
class ExecutorMessageHandlerTest {

    @Test
    void testCreateInstanceMessage() {
        val engine = mock(ApplicationInstanceEngine.class);
        val mh = new ExecutorMessageHandler(engine);
        when(engine.exists(anyString())).thenReturn(false);
        when(engine.startInstance(any(ApplicationInstanceSpec.class))).thenReturn(true);
        assertEquals(MessageDeliveryStatus.ACCEPTED,
                     mh.visit(new StartInstanceMessage(MessageHeader.controllerRequest(),
                                                       executor(),
                                                       testSpec()))
                             .getStatus());
    }

    @Test
    void testCreateInstanceMessageExists() {
        val engine = mock(ApplicationInstanceEngine.class);
        val mh = new ExecutorMessageHandler(engine);
        when(engine.exists(anyString())).thenReturn(true);
        assertEquals(MessageDeliveryStatus.FAILED,
                     mh.visit(new StartInstanceMessage(MessageHeader.controllerRequest(),
                                                       executor(),
                                                       testSpec()))
                             .getStatus());
    }

    @Test
    void testCreateInstanceMessageFail() {
        val engine = mock(ApplicationInstanceEngine.class);
        val mh = new ExecutorMessageHandler(engine);
        when(engine.exists(anyString())).thenReturn(false);
        when(engine.startInstance(any(ApplicationInstanceSpec.class))).thenReturn(false);
        assertEquals(MessageDeliveryStatus.FAILED,
                     mh.visit(new StartInstanceMessage(MessageHeader.controllerRequest(),
                                                       executor(),
                                                       testSpec()))
                             .getStatus());
    }

    @Test
    void testCreateInstanceMessageException() {
        val engine = mock(ApplicationInstanceEngine.class);
        val mh = new ExecutorMessageHandler(engine);
        when(engine.exists(anyString())).thenReturn(false);
        when(engine.startInstance(any(ApplicationInstanceSpec.class))).thenThrow(new IllegalStateException("Forced failure"));
        assertEquals(MessageDeliveryStatus.FAILED,
                     mh.visit(new StartInstanceMessage(MessageHeader.controllerRequest(),
                                                       executor(),
                                                       testSpec()))
                             .getStatus());
    }

    @Test
    void testStopInstanceMessage() {
        val engine = mock(ApplicationInstanceEngine.class);
        val mh = new ExecutorMessageHandler(engine);
        when(engine.exists(anyString())).thenReturn(true);
        when(engine.stopInstance(anyString())).thenReturn(true);
        assertEquals(MessageDeliveryStatus.ACCEPTED,
                     mh.visit(new StopInstanceMessage(MessageHeader.controllerRequest(),
                                                      executor(),
                                                      "blah"))
                             .getStatus());
    }

    @Test
    void testStopInstanceMessageWongId() {
        val engine = mock(ApplicationInstanceEngine.class);
        val mh = new ExecutorMessageHandler(engine);
        when(engine.exists(anyString())).thenReturn(false);
        when(engine.stopInstance(anyString())).thenReturn(true);
        assertEquals(MessageDeliveryStatus.FAILED,
                     mh.visit(new StopInstanceMessage(MessageHeader.controllerRequest(),
                                                      executor(),
                                                      "blah"))
                             .getStatus());
    }

    @Test
    void testStopInstanceMessageFail() {
        val engine = mock(ApplicationInstanceEngine.class);
        val mh = new ExecutorMessageHandler(engine);
        when(engine.exists(anyString())).thenReturn(true);
        when(engine.stopInstance(anyString())).thenReturn(false);
        assertEquals(MessageDeliveryStatus.FAILED,
                     mh.visit(new StopInstanceMessage(MessageHeader.controllerRequest(),
                                                      executor(),
                                                      "blah"))
                             .getStatus());
    }

    @Test
    void testStopInstanceMessageThrow() {
        val engine = mock(ApplicationInstanceEngine.class);
        val mh = new ExecutorMessageHandler(engine);
        when(engine.exists(anyString())).thenReturn(true);
        when(engine.stopInstance(anyString())).thenThrow(new IllegalArgumentException("Forced fail"));
        assertEquals(MessageDeliveryStatus.FAILED,
                     mh.visit(new StopInstanceMessage(MessageHeader.controllerRequest(),
                                                      executor(),
                                                      "blah"))
                             .getStatus());
    }

    @Test
    void testBlacklist() {
        val engine = mock(ApplicationInstanceEngine.class);
        val mh = new ExecutorMessageHandler(engine);
        assertEquals(MessageDeliveryStatus.ACCEPTED,
                     mh.visit(new BlacklistExecutorMessage(MessageHeader.controllerRequest(),
                                                           executor())).getStatus());
    }

    @Test
    void testBlacklistException() {
        val engine = mock(ApplicationInstanceEngine.class);
        doThrow(new IllegalArgumentException()).when(engine).blacklist();
        val mh = new ExecutorMessageHandler(engine);
        assertEquals(MessageDeliveryStatus.FAILED,
                     mh.visit(new BlacklistExecutorMessage(MessageHeader.controllerRequest(),
                                                           executor())).getStatus());
    }

    @Test
    void testUnBlacklist() {
        val engine = mock(ApplicationInstanceEngine.class);
        val mh = new ExecutorMessageHandler(engine);
        assertEquals(MessageDeliveryStatus.ACCEPTED,
                     mh.visit(new UnBlacklistExecutorMessage(MessageHeader.controllerRequest(),
                                                             executor())).getStatus());
    }

    @Test
    void testUnBlacklistException() {
        val engine = mock(ApplicationInstanceEngine.class);
        doThrow(new IllegalArgumentException()).when(engine).unblacklist();
        val mh = new ExecutorMessageHandler(engine);
        assertEquals(MessageDeliveryStatus.FAILED,
                     mh.visit(new UnBlacklistExecutorMessage(MessageHeader.controllerRequest(),
                                                           executor())).getStatus());
    }
}