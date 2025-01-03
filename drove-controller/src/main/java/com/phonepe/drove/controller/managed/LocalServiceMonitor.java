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

import com.google.common.annotations.VisibleForTesting;
import com.phonepe.drove.common.CommonUtils;
import com.phonepe.drove.common.coverageutils.IgnoreInJacocoGeneratedReport;
import com.phonepe.drove.controller.engine.LocalServiceLifecycleManagementEngine;
import com.phonepe.drove.controller.engine.ValidationStatus;
import com.phonepe.drove.controller.resourcemgmt.ClusterResourcesDB;
import com.phonepe.drove.controller.resourcemgmt.ExecutorHostInfo;
import com.phonepe.drove.controller.statedb.ClusterStateDB;
import com.phonepe.drove.controller.statedb.LocalServiceStateDB;
import com.phonepe.drove.models.instance.LocalServiceInstanceState;
import com.phonepe.drove.models.localservice.LocalServiceInfo;
import com.phonepe.drove.models.localservice.LocalServiceInstanceInfo;
import com.phonepe.drove.models.localservice.LocalServiceState;
import com.phonepe.drove.models.operation.LocalServiceOperation;
import com.phonepe.drove.models.operation.localserviceops.LocalServiceAdjustInstancesOperation;
import com.phonepe.drove.models.operation.localserviceops.LocalServiceStopInstancesOperation;
import io.appform.signals.signals.ScheduledSignal;
import io.dropwizard.lifecycle.Managed;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import ru.vyarus.dropwizard.guice.module.installer.order.Order;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Date;
import java.util.EnumSet;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Monitors Local Services to ensure required instances are present on all executors
 */
@Slf4j
@Order(35)
@Singleton
public class LocalServiceMonitor implements Managed {
    private static final String HANDLER_NAME = "LOCAL_SERVICE_CHECK_MONITOR";
    private final ScheduledSignal refreshSignal;

    private final ClusterResourcesDB clusterResourcesDB;
    private final ClusterStateDB clusterStateDB;
    private final LocalServiceStateDB stateDB;
    private final LocalServiceLifecycleManagementEngine localServiceEngine;
    private final LeadershipEnsurer leadershipEnsurer;

    @Inject
    @IgnoreInJacocoGeneratedReport
    public LocalServiceMonitor(
            ClusterResourcesDB clusterResourcesDB,
            ClusterStateDB clusterStateDB,
            LocalServiceStateDB stateDB,
            LocalServiceLifecycleManagementEngine localServiceEngine,
            LeadershipEnsurer leadershipEnsurer) {
        this(clusterResourcesDB,
             clusterStateDB,
             stateDB,
             localServiceEngine,
             leadershipEnsurer,
             ScheduledSignal.builder()
                .initialDelay(Duration.ofSeconds(5))
                .interval(Duration.ofSeconds(30))
                .build());
    }

    @VisibleForTesting
    LocalServiceMonitor(
            ClusterResourcesDB clusterResourcesDB,
            ClusterStateDB clusterStateDB,
            LocalServiceStateDB stateDB,
            LocalServiceLifecycleManagementEngine localServiceEngine,
            LeadershipEnsurer leadershipEnsurer,
            ScheduledSignal refreshSignal) {
        this.clusterResourcesDB = clusterResourcesDB;
        this.clusterStateDB = clusterStateDB;
        this.stateDB = stateDB;
        this.localServiceEngine = localServiceEngine;
        this.leadershipEnsurer = leadershipEnsurer;
        this.refreshSignal = refreshSignal;
    }

    @Override
    public void start() throws Exception {
        refreshSignal.connect(HANDLER_NAME, this::checkAllServices);
    }

    @Override
    public void stop() throws Exception {
        log.debug("Shutting down {}", this.getClass().getSimpleName());
        refreshSignal.disconnect(HANDLER_NAME);
        refreshSignal.close();
        log.debug("Shut down {}", this.getClass().getSimpleName());
    }

    private void checkAllServices(Date date) {
        checkAllServices();
        log.info("Local services check completed at {}", date);
    }

    private void checkAllServices() {
        if (!leadershipEnsurer.isLeader()) {
            log.info("Skipping local services check as I'm not the leader");
            return;
        }
        if (CommonUtils.isInMaintenanceWindow(clusterStateDB.currentState().orElse(null))) {
            log.warn("Local service check skipped as cluster is in maintenance window");
            return;
        }
        val relevantStates = EnumSet.of(LocalServiceState.ACTIVE, LocalServiceState.INACTIVE);
        val services = stateDB.services(0, Integer.MAX_VALUE)
                .stream()
                .filter(serviceInfo -> relevantStates.contains(localServiceEngine.currentState(serviceInfo.getServiceId())
                                                                       .orElse(LocalServiceState.DESTROYED)))
                .collect(Collectors.toMap(LocalServiceInfo::getServiceId, Function.identity()));
        //Ensure active executors have instances
        //Kill instances for inactive executors
        val executors = clusterResourcesDB.currentSnapshot(false);
        val liveExecutors = new ArrayList<ExecutorHostInfo>();
        val blacklistedExecutors = new ArrayList<ExecutorHostInfo>();
        executors.forEach(executorHostInfo -> {
            if (clusterResourcesDB.isBlacklisted(executorHostInfo.getExecutorId())) {
                blacklistedExecutors.add(executorHostInfo);
            }
            else {
                liveExecutors.add(executorHostInfo);
            }
        });

        handleServiceInstances(services, liveExecutors);

        //Now kill all service instances running on blacklisted executors
        //This _could_ be done in adjust instances, it is more efficient to generate commands here in a single shot
        val instancesToBeStopped = blacklistedExecutors.stream()
                .flatMap(executorHostInfo -> executorHostInfo.getNodeData().getServiceInstances().stream())
                .collect(Collectors.groupingBy(LocalServiceInstanceInfo::getServiceId,
                                               Collectors.mapping(LocalServiceInstanceInfo::getInstanceId,
                                                                  Collectors.toSet())));
        instancesToBeStopped.forEach((serviceId, instances) -> {
            log.info("Requesting to stop {} instances for service {}", instances.size(), serviceId);
            notifyOperation(new LocalServiceStopInstancesOperation(serviceId, instances, null));
        });
    }

    @SuppressWarnings("java:S1301")
    private void handleServiceInstances(Map<String, LocalServiceInfo> services, ArrayList<ExecutorHostInfo> liveExecutors) {
        services.forEach((serviceId, serviceInfo) -> {
            val currInstances = stateDB.instances(serviceId, LocalServiceInstanceState.ACTIVE_STATES, false);
            switch (serviceInfo.getActivationState()) {
                case ACTIVE -> {
                    val requiredInstances = serviceInfo.getInstancesPerHost() * liveExecutors.size();
                    if (currInstances.size() != requiredInstances) {
                        log.info("Discrepancy found in the instance count for service {}. Required: {} Actual: {}",
                                 serviceId, requiredInstances, currInstances.size());
                        notifyOperation(new LocalServiceAdjustInstancesOperation(serviceId, null));
                    }
                }
                case INACTIVE -> {
                    if (!currInstances.isEmpty()) {
                        log.info("Instances found for inactive service: {}. Need to be scaled", serviceId);
                        notifyOperation(new LocalServiceAdjustInstancesOperation(serviceId, null));
                    }
                }
            }
        });
    }

    private void notifyOperation(final LocalServiceOperation operation) {
        val res = localServiceEngine.handleOperation(operation);
        if (!res.getStatus().equals(ValidationStatus.SUCCESS)) {
            log.error("Error sending command to state machine. Error: " + res.getMessages());
        }
    }
}
