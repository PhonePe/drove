/*
 *  Copyright (c) 2022 Original Author(s), PhonePe India Pvt. Ltd.
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

package com.phonepe.drove.models.events.events;

/**
 * To handle event types in a type-safe manner
 */
public interface DroveEventVisitor<T> {
    T visit(DroveAppStateChangeEvent appStateChanged);

    T visit(DroveInstanceStateChangeEvent instanceStateChanged);

    T visit(DroveTaskStateChangeEvent taskStateChanged);

    T visit(DroveExecutorAddedEvent executorAdded);

    T visit(DroveExecutorRemovedEvent executorRemoved);

    T visit(DroveExecutorBlacklistedEvent executorBlacklisted);

    T visit(DroveExecutorUnblacklistedEvent executorUnBlacklisted);

    T visit(DroveClusterMaintenanceModeSetEvent clusterMaintenanceModeSet);

    T visit(DroveClusterMaintenanceModeRemovedEvent clusterMaintenanceModeRemoved);

    T visit(DroveClusterLeadershipAcquiredEvent leadershipAcquired);

    T visit(DroveClusterLeadershipLostEvent leadershipLost);


}
