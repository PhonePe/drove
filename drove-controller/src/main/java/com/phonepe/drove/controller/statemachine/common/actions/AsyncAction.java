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

package com.phonepe.drove.controller.statemachine.common.actions;

import com.google.common.base.Strings;
import com.phonepe.drove.controller.engine.jobs.BooleanResponseCombiner;
import com.phonepe.drove.jobexecutor.JobExecutionResult;
import com.phonepe.drove.jobexecutor.JobExecutor;
import com.phonepe.drove.jobexecutor.JobTopology;
import com.phonepe.drove.statemachine.StateData;
import io.appform.functionmetrics.MonitoredFunction;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Infrastructure class to handle topology execution as a part of actions
 */
@Slf4j
public abstract class AsyncAction<T, S extends Enum<S>, C extends JobEnabledContext<D>, D> extends OperationDrivenAction<T, S, C, D> {
    private final Lock jobLock = new ReentrantLock();
    private final Condition condition = jobLock.newCondition();
    private final JobExecutor<Boolean> jobExecutor;
    private final List<AsyncActionPlugin<T, S, C, D>> plugins;
    private final AtomicBoolean done = new AtomicBoolean(false);
    private final AtomicReference<StateData<S, T>> result = new AtomicReference<>();

    protected AsyncAction(JobExecutor<Boolean> jobExecutor, List<AsyncActionPlugin<T, S, C, D>> plugins) {
        this.jobExecutor = jobExecutor;
        this.plugins = Objects.requireNonNullElseGet(plugins, List::of);
    }

    @Override
    @MonitoredFunction
    protected StateData<S, T> commandReceived(C context, StateData<S, T> currentState, D operation) {
        runPluginsPreCreate(context, operation);
        val topology = jobsToRun(context, currentState, operation).orElse(null);
        if (topology == null) {
            return handleEmptyTopology(context, currentState);
        }
        runPluginsPreSubmit(context, operation, topology);
        val jobId = jobExecutor.schedule(topology,
                                         new BooleanResponseCombiner(),
                                         jobResult -> jobCompleted(context, currentState, operation, jobResult));
        context.setJobId(jobId);
        jobLock.lock();
        try {
            while (!done.get()) {
                condition.await();
            }
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        finally {
            jobLock.unlock();
        }
        return result.get();
    }

    protected abstract StateData<S, T> handleEmptyTopology(C context, StateData<S, T> currentState);

    protected boolean cancelCurrentJobs(final C context) {
        if (Strings.isNullOrEmpty(context.getJobId())) {
            return false;
        }
        jobExecutor.cancel(context.getJobId());
        return true;
    }

    protected abstract Optional<JobTopology<Boolean>> jobsToRun(
            C context,
            StateData<S, T> currentState,
            D operation);

    protected abstract StateData<S, T> processResult(
            C context,
            StateData<S, T> currentState,
            D operation,
            JobExecutionResult<Boolean> executionResult);


    private void jobCompleted(
            C context,
            StateData<S, T> currentState,
            D operation,
            JobExecutionResult<Boolean> executionResult) {
        jobLock.lock();
        try {
            this.result.set(processResult(context, currentState, operation, executionResult));
            done.set(true);
            condition.signalAll();
        }
        catch (Exception e) {
            log.error("Error processing result: ", e);
        }
        finally {
            runPluginAfterResult(context, operation, executionResult);
            jobLock.unlock();
        }
    }

    private void runPluginsPreCreate(C context, D operation) {
        try {
            plugins.forEach(plugin -> plugin.beforeTopologyCreation(context, operation));
        }
        catch (Exception e) {
            log.error("Error running plugin beforeTopologyCreation: ", e);
        }
    }

    private void runPluginsPreSubmit(C context, D operation, JobTopology<Boolean> topology) {
        try {
            plugins.forEach(plugin -> plugin.beforeTopologySubmission(context, operation, topology));
        }
        catch (Exception e) {
            log.error("Error running plugin beforeTopologySubmission: ", e);
        }
    }

    private void runPluginAfterResult(C context, D operation, JobExecutionResult<Boolean> executionResult) {
        try {
            plugins.forEach(plugin -> plugin.afterResultGenerated(context, operation, executionResult));
        }
        catch (Exception e) {
            log.error("Error running plugin afterResultGenerated: ", e);
        }
    }


}
