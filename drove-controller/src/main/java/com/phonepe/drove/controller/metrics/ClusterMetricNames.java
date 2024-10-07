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

package com.phonepe.drove.controller.metrics;

import lombok.experimental.UtilityClass;

/**
 * Names of all cluster level metrics
 */
@UtilityClass
public final class ClusterMetricNames {

    @UtilityClass
    public static final class Gauges {
        public static final String CLUSTER_CPU_USED = "cluster.resources.cpu.used";
        public static final String CLUSTER_CPU_FREE = "cluster.resources.cpu.free";
        public static final String CLUSTER_CPU_TOTAL = "cluster.resources.cpu.total";
        public static final String CLUSTER_MEMORY_USED = "cluster.resources.memory.used_mb";
        public static final String CLUSTER_MEMORY_FREE = "cluster.resources.memory.free_mb";
        public static final String CLUSTER_MEMORY_TOTAL = "cluster.resources.memory.total_mb";

        public static final String CLUSTER_EXECUTORS_ACTIVE = "cluster.executors.active";
        public static final String CLUSTER_EXECUTORS_INACTIVE = "cluster.executors.inactive";

        public static final String CLUSTER_APPLICATIONS_RUNNING = "cluster.applications.running";
        public static final String CLUSTER_APPLICATIONS_TOTAL = "cluster.applications.total";
        public static final String CLUSTER_APPLICATIONS_INSTANCES_ACTIVE = "cluster.applications.instances.active";
        public static final String CLUSTER_APPLICATIONS_INSTANCES_INACTIVE = "cluster.applications.instances.inactive";

        public static final String CLUSTER_TASK_INSTANCES_ACTIVE = "cluster.tasks.instances.active";
    }

    @UtilityClass
    public static final class Meters {
        public static final String CLUSTER_EVENTS = "cluster.events";
    }


}
