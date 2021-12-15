package com.phonepe.drove.executor.engine;

import com.github.dockerjava.api.DockerClient;
import com.phonepe.drove.common.StateData;
import com.phonepe.drove.common.model.InstanceSpec;
import com.phonepe.drove.common.model.MessageResponse;
import com.phonepe.drove.common.model.executor.ExecutorMessage;
import com.phonepe.drove.executor.InstanceActionFactory;
import com.phonepe.drove.executor.Utils;
import com.phonepe.drove.executor.managed.ExecutorIdManager;
import com.phonepe.drove.executor.model.ExecutorInstanceInfo;
import com.phonepe.drove.executor.resourcemgmt.ResourceDB;
import com.phonepe.drove.executor.statemachine.BlacklistingManager;
import com.phonepe.drove.executor.statemachine.InstanceStateMachine;
import com.phonepe.drove.models.info.resources.allocation.CPUAllocation;
import com.phonepe.drove.models.info.resources.allocation.MemoryAllocation;
import com.phonepe.drove.models.info.resources.allocation.ResourceAllocationVisitor;
import com.phonepe.drove.models.instance.InstanceInfo;
import com.phonepe.drove.models.instance.InstanceState;
import io.appform.functionmetrics.MonitoredFunction;
import io.appform.signals.signals.ConsumingParallelSignal;
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
import java.util.stream.Collectors;

import static ch.qos.logback.classic.ClassicConstants.FINALIZE_SESSION_MARKER;

/**
 *
 */
@Slf4j
public class InstanceEngine implements Closeable {
    private final ExecutorIdManager executorIdManager;
    private final ExecutorService service;
    private final InstanceActionFactory actionFactory;
    private final ResourceDB resourceDB;
    private final BlacklistingManager blacklistManager;
    private final Map<String, SMInfo> stateMachines;
    private final ConsumingParallelSignal<InstanceInfo> stateChanged = new ConsumingParallelSignal<>();
    private final DockerClient client;


    public InstanceEngine(
            final ExecutorIdManager executorIdManager, ExecutorService service,
            InstanceActionFactory actionFactory,
            ResourceDB resourceDB, BlacklistingManager blacklistManager, DockerClient client) {
        this.executorIdManager = executorIdManager;
        this.service = service;
        this.actionFactory = actionFactory;
        this.resourceDB = resourceDB;
        this.blacklistManager = blacklistManager;
        this.client = client;
        this.stateMachines = new ConcurrentHashMap<>();
    }

    public MessageResponse handleMessage(final ExecutorMessage message) {
        return message.accept(new ExecutorMessageHandler(this));
    }

    public boolean exists(final String instanceId) {
        return stateMachines.containsKey(instanceId);
    }

    public boolean startInstance(final InstanceSpec spec) {
        val currDate = new Date();
        return registerInstance(spec.getInstanceId(),
                                spec,
                                StateData.create(InstanceState.PENDING,
                                                 new ExecutorInstanceInfo(spec.getAppId(),
                                                                          spec.getAppName(),
                                                                          spec.getInstanceId(),
                                                                          executorIdManager.executorId().orElse(null),
                                                                          null,
                                                                          spec.getResources(),
                                                                          Collections.emptyMap(),
                                                                          currDate,
                                                                          currDate)));
    }

    @MonitoredFunction
    public boolean registerInstance(
            final String instanceId,
            final InstanceSpec spec,
            final StateData<InstanceState, ExecutorInstanceInfo> currentState) {
        if (!lockRequiredResources(spec)) {
            return false;
        }
        val stateMachine = new InstanceStateMachine(executorIdManager.executorId().orElse(null),
                                                    spec,
                                                    currentState,
                                                    actionFactory,
                                                    client);
        stateMachine.onStateChange().connect(this::handleStateChange);
        val f = service.submit(() -> {
            MDC.put("instanceLogId", spec.getAppId() + ":" + spec.getInstanceId());
            InstanceState state = null;
            do {
                state = stateMachine.execute();
            } while (!state.isTerminal());
            log.info(FINALIZE_SESSION_MARKER, "Completed");
            MDC.remove("instanceLogId");
            return state;
        });
        stateMachines.put(instanceId, new SMInfo(stateMachine, f));
        return true;
    }

    @MonitoredFunction
    public boolean stopInstance(final String instanceId) {
        val info = stateMachines.get(instanceId);
        if (null == info) {
            log.error("No such instance: {}. Nothing will be stopped", instanceId);
            return false;
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
        return true;
    }

    public Optional<InstanceInfo> currentState(final String instanceId) {
        val smInfo = stateMachines.get(instanceId);
        if (null == smInfo) {
            return Optional.empty();
        }
        return Optional.of(Utils.convert(smInfo.getStateMachine().getCurrentState()));
    }

    public List<InstanceInfo> currentState() {
        return stateMachines.values()
                .stream()
                .map(v -> Utils.convert(v.getStateMachine().getCurrentState()))
                .collect(Collectors.toUnmodifiableList());
    }

    @MonitoredFunction
    public void blacklist() {
        blacklistManager.blacklist();
    }

    @MonitoredFunction
    public void unblacklist() {
        blacklistManager.unblacklist();
    }

    @Override
    public void close() throws IOException {
        //TODO::STOP ALL SMs, get all states, then shutdown the pool
    }

    public ConsumingParallelSignal<InstanceInfo> onStateChange() {
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
                                .forEach((node, allocCpus) -> resourceUsage.compute(node, (nodeId, nodeInfo) -> {
                                    val info = nodeInfo(nodeInfo);
                                    info.setAvailableCores(allocCpus);
                                    return info;

                                }));
                        return null;
                    }

                    @Override
                    public Void visit(MemoryAllocation memory) {
                        memory.getMemoryInMB()
                                .forEach((node, allocMem) -> resourceUsage.compute(node, (nodeId, nodeInfo) -> {
                                    val info = nodeInfo(nodeInfo);
                                    info.setMemoryInMB(allocMem);
                                    return info;
                                }));
                        return null;
                    }
                }));
        return resourceDB.lockResources(new ResourceDB.ResourceUsage(spec.getInstanceId(),
                                                                     ResourceDB.ResourceLockType.HARD,
                                                                     resourceUsage));
    }

    private ResourceDB.NodeInfo nodeInfo(ResourceDB.NodeInfo nodeInfo) {
        return nodeInfo == null
               ? new ResourceDB.NodeInfo(new HashSet<>(), 0L)
               : nodeInfo;
    }

    @MonitoredFunction
    private void handleStateChange(StateData<InstanceState, ExecutorInstanceInfo> currentState) {
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
        val instanceInfo = Utils.convert(currentState);
        stateChanged.dispatch(instanceInfo);
    }

}
