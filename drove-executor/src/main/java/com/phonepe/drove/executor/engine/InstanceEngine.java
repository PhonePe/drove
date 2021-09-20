package com.phonepe.drove.executor.engine;

import com.phonepe.drove.common.ClockPulseGenerator;
import com.phonepe.drove.common.StateData;
import com.phonepe.drove.common.model.InstanceSpec;
import com.phonepe.drove.common.model.MessageHeader;
import com.phonepe.drove.common.model.MessageResponse;
import com.phonepe.drove.common.model.controller.InstanceStateReportMessage;
import com.phonepe.drove.common.model.executor.ExecutorMessage;
import com.phonepe.drove.common.model.resources.allocation.CPUAllocation;
import com.phonepe.drove.common.model.resources.allocation.MemoryAllocation;
import com.phonepe.drove.common.model.resources.allocation.ResourceAllocationVisitor;
import com.phonepe.drove.executor.InstanceActionFactory;
import com.phonepe.drove.executor.resource.ResourceDB;
import com.phonepe.drove.executor.statemachine.InstanceStateMachine;
import com.phonepe.drove.models.instance.InstanceInfo;
import com.phonepe.drove.models.instance.InstanceState;
import io.appform.signals.signals.ConsumingParallelSignal;
import io.dropwizard.util.Duration;
import lombok.Setter;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.slf4j.MDC;

import java.io.Closeable;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/**
 *
 */
@Slf4j
public class InstanceEngine implements Closeable {
    private final ExecutorService service;
    private final InstanceActionFactory actionFactory;
    private final ResourceDB resourceDB;
    private final Map<String, SMInfo> stateMachines;
    private final ConsumingParallelSignal<StateData<InstanceState, InstanceInfo>> stateChanged;
    private final ClockPulseGenerator clockPulseGenerator;
    @Setter
    private ExecutorCommunicator communicator;

    public InstanceEngine(
            ExecutorService service,
            InstanceActionFactory actionFactory,
            ResourceDB resourceDB) {
        this.service = service;
        this.actionFactory = actionFactory;
        this.resourceDB = resourceDB;
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

    public boolean exists(final String instanceId) {
        return stateMachines.containsKey(instanceId);
    }

    public boolean startInstance(final InstanceSpec spec) {
        return registerInstance(spec.getInstanceId(), spec, StateData.create(InstanceState.PENDING, null));
    }

    public boolean registerInstance(
            final String instanceId,
            final InstanceSpec spec,
            final StateData<InstanceState, InstanceInfo> currentState) {
        if (!lockRequiredResources(spec)) {
            return false;
        }
        val stateMachine = new InstanceStateMachine(spec, currentState, actionFactory);
        stateMachine.onStateChange().connect(this::handleStateChange);
        val f = service.submit(() -> {
            MDC.put("instanceId", spec.getInstanceId());
            InstanceState state = null;
            do {
                state = stateMachine.execute();
            } while (!state.isTerminal());
            MDC.remove("instanceId");
            return state;
        });
        stateMachines.put(instanceId, new SMInfo(stateMachine, f));
        return true;
    }

    public void stopInstance(final String instanceId) {
        val info = stateMachines.get(instanceId);
        if (null == info) {
            log.error("No such instance: {}. Nothing will be stopped", instanceId);
            return;
        }
        info.getStateMachine().stop();
        service.submit(() -> {
            try {
                val finalState = info.getStateMachineFuture().get();
                log.info("Final state: {}", finalState);
            }
            catch (Exception e) {
                log.error("Error stopping instance: ", e);
            }
        });
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


    private boolean lockRequiredResources(InstanceSpec spec) {
        val resourceUsage = new HashMap<Integer, ResourceDB.NodeInfo>();
        spec.getResources()
                .forEach(resourceRequirement -> resourceRequirement.accept(new ResourceAllocationVisitor<Void>() {
                    @Override
                    public Void visit(CPUAllocation cpu) {
                        cpu.getCores()
                                .forEach((node, allocCpus) -> {
                                    resourceUsage.compute(node, (nodeId, nodeInfo) -> {
                                        val info = nodeInfo(nodeInfo);
                                        info.setAvailableCores(allocCpus);
                                        return info;
                                    });
                                });
                        return null;
                    }

                    @Override
                    public Void visit(MemoryAllocation memory) {
                        memory.getMemoryInMB()
                                .forEach((node, allocMem) -> {
                                    resourceUsage.compute(node, (nodeId, nodeInfo) -> {
                                        val info = nodeInfo(nodeInfo);
                                        info.setMemoryInMB(allocMem);
                                        return info;
                                    });
                                });
                        return null;
                    }
                }));
        return resourceDB.lockResources(new ResourceDB.ResourceUsage(spec.getInstanceId(),
                                                                     ResourceDB.ResourceLockType.HARD,
                                                                     resourceUsage));
    }

    private ResourceDB.NodeInfo nodeInfo(ResourceDB.NodeInfo nodeInfo) {
        val updated = nodeInfo == null
                      ? new ResourceDB.NodeInfo(new HashSet<>(), 0L)
                      : nodeInfo;
        return updated;
    }

    private void handleStateChange(StateData<InstanceState, InstanceInfo> currentState) {
        val state = currentState.getState();
        log.info("Current state: {}. Terminal: {} Error: {}", currentState, state.isTerminal(), state.isError());
        if (state.isTerminal()) {
            val data = currentState.getData();
            if (null != data) {
                resourceDB.reclaimResources(currentState.getData().getInstanceId());
                stateMachines.remove(data.getInstanceId());
                log.info("State machine {} has been successfully terminated", data.getInstanceId());
            }
            else {
                log.warn("State data is not present");
            }
        }
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
