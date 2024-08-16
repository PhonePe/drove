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

package com.phonepe.drove.executor.engine;

import com.github.dockerjava.api.DockerClient;
import com.phonepe.drove.common.model.ApplicationInstanceSpec;
import com.phonepe.drove.common.model.DeploymentUnitSpec;
import com.phonepe.drove.common.model.DeploymentUnitSpecVisitor;
import com.phonepe.drove.common.model.TaskInstanceSpec;
import com.phonepe.drove.executor.ExecutorActionFactory;
import com.phonepe.drove.executor.managed.ExecutorIdManager;
import com.phonepe.drove.executor.model.DeployedExecutionObjectInfo;
import com.phonepe.drove.executor.resourcemgmt.ResourceManager;
import com.phonepe.drove.executor.statemachine.ExecutorActionBase;
import com.phonepe.drove.executor.statemachine.InstanceActionContext;
import com.phonepe.drove.executor.utils.ExecutorUtils;
import com.phonepe.drove.models.info.resources.allocation.CPUAllocation;
import com.phonepe.drove.models.info.resources.allocation.MemoryAllocation;
import com.phonepe.drove.models.info.resources.allocation.ResourceAllocationVisitor;
import com.phonepe.drove.models.interfaces.DeployedInstanceInfo;
import com.phonepe.drove.statemachine.StateData;
import com.phonepe.drove.statemachine.StateMachine;
import io.appform.functionmetrics.MonitoredFunction;
import io.appform.signals.signals.ConsumingParallelSignal;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.slf4j.MDC;

import java.io.Closeable;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.locks.StampedLock;
import java.util.stream.Collectors;

import static ch.qos.logback.classic.ClassicConstants.FINALIZE_SESSION_MARKER;
import static com.phonepe.drove.common.CommonUtils.instanceId;

/**
 *
 */
@Slf4j
@SuppressWarnings("unused")
public abstract class InstanceEngine<E extends DeployedExecutionObjectInfo, S extends Enum<S>,
        T extends DeploymentUnitSpec, I extends DeployedInstanceInfo> implements Closeable {
    private static final String LOG_ID = "instanceLogId";
    private final ExecutorIdManager executorIdManager;
    private final ExecutorService service;
    private final ExecutorActionFactory<E, S, T> actionFactory;
    private final ResourceManager resourceDB;
    private final Map<String, SMInfo<E, S, T>> stateMachines = new HashMap<>();
    private final StampedLock lock = new StampedLock();

    private final ConsumingParallelSignal<I> stateChanged = new ConsumingParallelSignal<>();
    private final DockerClient client;


    protected InstanceEngine(
            final ExecutorIdManager executorIdManager, ExecutorService service,
            ExecutorActionFactory<E, S, T> actionFactory,
            ResourceManager resourceDB, DockerClient client) {
        this.executorIdManager = executorIdManager;
        this.service = service;
        this.actionFactory = actionFactory;
        this.resourceDB = resourceDB;
        this.client = client;
    }

    public boolean exists(final String instanceId) {
        val stamp = lock.readLock();
        try {
            return stateMachines.containsKey(instanceId);
        }
        finally {
            lock.unlockRead(stamp);
        }
    }

    public Set<String> instanceIds(final Set<S> matchingStates) {
        val stamp = lock.readLock();
        try {
            return stateMachines.entrySet()
                    .stream()
                    .filter(e -> matchingStates.contains(e.getValue().getStateMachine().getCurrentState().getState()))
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toUnmodifiableSet());
        }
        finally {
            lock.unlockRead(stamp);
        }
    }

    public boolean startInstance(final T spec) {
        val currDate = new Date();
        return registerInstance(instanceId(spec),
                                spec,
                                createInitialState(spec, currDate, executorIdManager));
    }

    protected abstract StateData<S, E> createInitialState(
            T spec,
            Date currDate,
            ExecutorIdManager executorIdManager);

    @MonitoredFunction
    public boolean registerInstance(
            final String instanceId,
            final T spec,
            final StateData<S, E> currentState) {
        val stamp = lock.writeLock();
        try {
            if (!lockRequiredResources(spec)) {
                log.error("Could not lock required resources. Instance has been leaked");
                return false;
            }
            val stateMachine = createStateMachine(executorIdManager.executorId().orElse(null),
                                                  spec,
                                                  currentState,
                                                  actionFactory,
                                                  client);
            stateMachine.onStateChange().connect(this::handleStateChange);
            val f = service.submit(() -> {
                spec.accept(new DeploymentUnitSpecVisitor<Void>() {
                    @Override
                    public Void visit(ApplicationInstanceSpec applicationInstanceSpec) {
                        MDC.put(LOG_ID,
                                applicationInstanceSpec.getAppId() + ":" + applicationInstanceSpec.getInstanceId());
                        return null;
                    }

                    @Override
                    public Void visit(TaskInstanceSpec taskInstanceSpec) {
                        MDC.put(LOG_ID, taskInstanceSpec.getSourceAppName() + ":" + taskInstanceSpec.getTaskId());
                        return null;
                    }
                });
                S state = null;
                val lostState = lostState();
                do {
                    try {
                        state = stateMachine.execute();
                    }
                    catch (Exception e) {
                        state = lostState;
                        log.error("Error in state machine execution: {}", e.getMessage(), e);
                        handleStateChange(StateData.errorFrom(stateMachine.getCurrentState(),
                                                              lostState,
                                                              "SM Execution error: " + e.getMessage()));
                    }
                } while (!isTerminal(state));
                log.info(FINALIZE_SESSION_MARKER, "Completed");
                MDC.remove(LOG_ID);
                return state;
            });
            stateMachines.put(instanceId, new SMInfo<>(stateMachine, f));
            return true;
        }
        finally {
            lock.unlockWrite(stamp);
        }
    }

    protected abstract S lostState();

    protected abstract boolean isTerminal(S state);

    protected abstract boolean isError(S state);

    protected abstract boolean isRunning(S state);

    protected abstract StateMachine<E, Void, S, InstanceActionContext<T>, ExecutorActionBase<E, S, T>> createStateMachine(
            String executorId,
            T spec,
            StateData<S, E> currentState,
            ExecutorActionFactory<E, S, T> actionFactory,
            DockerClient client);

    protected abstract I convertStateToInstanceInfo(StateData<S, E> currentState);

    @MonitoredFunction
    public boolean stopInstance(final String instanceId) {
        val stamp = lock.writeLock();
        try {
            val info = stateMachines.get(instanceId);
            if (null == info) {
                log.error("No such instance: {}. Nothing will be stopped", instanceId);
                return false;
            }
            val currState = info.getStateMachine().getCurrentState().getState();
            if (!isRunning(currState)) {
                log.error("Cannot stop {} as it is not in active running state. Current state: {}",
                          instanceId,
                          currState);
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
        finally {
            lock.unlockWrite(stamp);
        }
    }

    public Optional<I> currentState(final String instanceId) {
        val stamp = lock.readLock();
        try {
            val smInfo = stateMachines.get(instanceId);
            if (null == smInfo) {
                return Optional.empty();
            }
            return Optional.of(convertStateToInstanceInfo(smInfo.getStateMachine().getCurrentState()));
        }
        finally {
            lock.unlockRead(stamp);
        }
    }

    public List<I> currentState() {
        val stamp = lock.readLock();
        try {
            return stateMachines.values()
                    .stream()
                    .map(v -> convertStateToInstanceInfo(v.getStateMachine().getCurrentState()))
                    .toList();
        }
        finally {
            lock.unlockRead(stamp);
        }
    }


    @Override
    public void close() throws IOException {
        //Nothing to do here. Do not shut down SM as this might just be for maintenance
    }

    public ConsumingParallelSignal<I> onStateChange() {
        return stateChanged;
    }


    @Value
    private static class SMInfo<E extends DeployedExecutionObjectInfo, S extends Enum<S>,
            T extends DeploymentUnitSpec> {
        StateMachine<E, Void, S, InstanceActionContext<T>, ExecutorActionBase<E, S, T>> stateMachine;
        Future<S> stateMachineFuture;
    }


    private boolean lockRequiredResources(DeploymentUnitSpec spec) {
        val resourceUsage = new HashMap<Integer, ResourceManager.NodeInfo>();
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
        return resourceDB.lockResources(new ResourceManager.ResourceUsage(instanceId(spec),
                                                                          ResourceManager.ResourceLockType.HARD,
                                                                          resourceUsage));
    }

    private ResourceManager.NodeInfo nodeInfo(ResourceManager.NodeInfo nodeInfo) {
        return Objects.requireNonNullElse(nodeInfo,
                                          new ResourceManager.NodeInfo(Set.of(), Map.of(), 0, new HashSet<>(), 0L));
    }

    @MonitoredFunction
    private void handleStateChange(StateData<S, E> currentState) {
        val state = currentState.getState();
        log.info("Current state: {}. Terminal: {} Error: {}", currentState, isTerminal(state), isError(state));
        if (isTerminal(state)) {
            val data = currentState.getData();
            if (null != data) {
                val instanceId = ExecutorUtils.instanceId(data);
                resourceDB.reclaimResources(instanceId);
                stateMachines.remove(instanceId);
                log.info("State machine {} has been successfully terminated", instanceId);
            }
            else {
                log.warn("State data is not present");
            }
        }
        val instanceInfo = convertStateToInstanceInfo(currentState);
        stateChanged.dispatch(instanceInfo);
    }


}
