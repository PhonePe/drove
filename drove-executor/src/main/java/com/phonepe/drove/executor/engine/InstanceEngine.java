package com.phonepe.drove.executor.engine;

import com.phonepe.drove.common.ClockPulseGenerator;
import com.phonepe.drove.common.StateData;
import com.phonepe.drove.common.messages.controller.InstanceStateReportMessage;
import com.phonepe.drove.common.messages.executor.ExecutorMessage;
import com.phonepe.drove.executor.statemachine.InstanceStateMachine;
import com.phonepe.drove.internalmodels.InstanceSpec;
import com.phonepe.drove.internalmodels.MessageHeader;
import com.phonepe.drove.internalmodels.MessageResponse;
import com.phonepe.drove.models.instance.InstanceInfo;
import com.phonepe.drove.models.instance.InstanceState;
import io.appform.signals.signals.ConsumingParallelSignal;
import io.dropwizard.util.Duration;
import lombok.Setter;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import java.io.Closeable;
import java.io.IOException;
import java.util.Date;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/**
 *
 */
@Slf4j
public class InstanceEngine implements Closeable {
    private final ExecutorService service;
    private final Map<String, SMInfo> stateMachines;
    private final ConsumingParallelSignal<StateData<InstanceState, InstanceInfo>> stateChanged;
    private final ClockPulseGenerator clockPulseGenerator;
    @Setter
    private ExecutorCommunicator communicator;

    public InstanceEngine(ExecutorService service) {
        this.service = service;
        this.stateMachines = new ConcurrentHashMap<>();
        stateChanged = new ConsumingParallelSignal<>();
        clockPulseGenerator = new ClockPulseGenerator("scheduled-reporting-pulse-generator",
                                                      Duration.seconds(30),
                                                      Duration.seconds(10));
        clockPulseGenerator.onPulse().connect(this::sendStatusReport);
    }

    public MessageResponse handleMessage(final ExecutorMessage message) {
        return message.accept(new ExecutorMessageHandler(this));
    }

    public void startInstance(final InstanceSpec start) {
        val sm = new InstanceStateMachine(start, StateData.create(InstanceState.PENDING, null));
        sm.onStateChange().connect(this::handleStateChange);
        val f = service.submit(() -> {
            InstanceState state = null;
            do {
                state = sm.execute();
            } while (!state.isTerminal());
            return state;
        });
        stateMachines.put(start.getInstanceId(), new SMInfo(sm, f));
    }

    public void stopInstance(final String instanceId) {
        val info = stateMachines.get(instanceId);
        if (null == info) {
            log.error("No such instance: {}. Nothing will be stopped", instanceId);
            return;
        }
        info.getStateMachine().stop();
        try {
            val finalState = info.getStateMachineFuture().get();
            log.info("Final state: {}", finalState);
            stateMachines.remove(instanceId);
        }
        catch (Exception e) {
            log.error("Error stopping instance");
        }
    }

    public Optional<StateData<InstanceState, InstanceInfo>> currentState(final String instanceId) {
        val smInfo = stateMachines.get(instanceId);
        if (null == smInfo) {
            return Optional.empty();
        }
        return Optional.ofNullable(smInfo.getStateMachine().getCurrentState());
    }

    @Override
    public void close() throws IOException {
        //TODO::STOP ALL SMs, get all states, then shutdown the pool
        clockPulseGenerator.close();
    }

    public ConsumingParallelSignal<StateData<InstanceState, InstanceInfo>> onStateChange() {
        return stateChanged;
    }

    @Value
    private static class SMInfo {
        InstanceStateMachine stateMachine;
        Future<InstanceState> stateMachineFuture;
    }

    private void handleStateChange(StateData<InstanceState, InstanceInfo> currentState) {
        log.info("Current state: {}", currentState);
        communicator.send(instanceStateMessage(currentState));
        stateChanged.dispatch(currentState);
        //TODO::SEND STATE UPDATE TO CONTROLLER
    }

    private void sendStatusReport(final Date now) {
        /*stateMachines.values().forEach(smi -> {
            communicator.send(instanceStateMessage(smi.getStateMachine().getCurrentState()));
        });*/
    }

    private InstanceStateReportMessage instanceStateMessage(StateData<InstanceState, InstanceInfo> currentState) {
        return new InstanceStateReportMessage(MessageHeader.executorRequest(),
                                              currentState.getData());
    }
}
