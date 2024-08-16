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

package com.phonepe.drove.controller.statemachine.applications;

import com.google.common.base.Strings;
import com.phonepe.drove.controller.engine.jobs.BooleanResponseCombiner;
import com.phonepe.drove.jobexecutor.JobExecutionResult;
import com.phonepe.drove.jobexecutor.JobExecutor;
import com.phonepe.drove.jobexecutor.JobTopology;
import com.phonepe.drove.controller.statedb.ApplicationInstanceInfoDB;
import com.phonepe.drove.models.application.ApplicationInfo;
import com.phonepe.drove.models.application.ApplicationState;
import com.phonepe.drove.models.instance.InstanceState;
import com.phonepe.drove.models.operation.ApplicationOperation;
import io.appform.functionmetrics.MonitoredFunction;
import com.phonepe.drove.statemachine.StateData;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 *
 */
@Slf4j
public abstract class AppAsyncAction extends OperationDrivenAppAction {
    private final Lock jobLock = new ReentrantLock();
    private final Condition condition = jobLock.newCondition();
    private final JobExecutor<Boolean> jobExecutor;
    private final ApplicationInstanceInfoDB instanceInfoDB;
    private final AtomicBoolean done = new AtomicBoolean(false);
    private final AtomicReference<StateData<ApplicationState, ApplicationInfo>> result = new AtomicReference<>();

    protected AppAsyncAction(JobExecutor<Boolean> jobExecutor, ApplicationInstanceInfoDB instanceInfoDB) {
        this.jobExecutor = jobExecutor;
        this.instanceInfoDB = instanceInfoDB;
    }

    @Override
    @MonitoredFunction
    protected StateData<ApplicationState, ApplicationInfo> commandReceived(
            AppActionContext context,
            StateData<ApplicationState, ApplicationInfo> currentState,
            ApplicationOperation operation) {
        val topology = jobsToRun(context, currentState, operation).orElse(null);
        if(topology == null) {
            if(instanceInfoDB.instanceCount(context.getAppId(), InstanceState.HEALTHY) == 0) {
                return StateData.from(currentState, ApplicationState.MONITORING);
            }
            return StateData.from(currentState, ApplicationState.RUNNING);
        }
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

    protected boolean cancelCurrentJobs(final AppActionContext context) {
        if(Strings.isNullOrEmpty(context.getJobId())) {
            return false;
        }
        jobExecutor.cancel(context.getJobId());
        return true;
    }

    protected abstract Optional<JobTopology<Boolean>> jobsToRun(
            AppActionContext context,
            StateData<ApplicationState, ApplicationInfo> currentState,
            ApplicationOperation operation);

    protected abstract StateData<ApplicationState, ApplicationInfo> processResult(
            AppActionContext context,
            StateData<ApplicationState, ApplicationInfo> currentState,
            ApplicationOperation operation,
            JobExecutionResult<Boolean> executionResult);


    private void jobCompleted(
            AppActionContext context,
            StateData<ApplicationState, ApplicationInfo> currentState,
            ApplicationOperation operation,
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
            jobLock.unlock();
        }
    }

}
