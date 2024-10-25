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

package com.phonepe.drove.models.events.events;


import lombok.AccessLevel;
import lombok.AllArgsConstructor;

/**
 * An adapter that can be subclassed to override handling only for relevant events.
 */
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class DroveEventVisitorAdapter<T> implements DroveEventVisitor<T> {
    private final T defaultValue;

    @Override
    public T visit(DroveAppStateChangeEvent appStateChanged) {
        return defaultValue;
    }

    @Override
    public T visit(DroveInstanceStateChangeEvent instanceStateChanged) {
        return defaultValue;
    }

    @Override
    public T visit(DroveTaskStateChangeEvent taskStateChanged) {
        return defaultValue;
    }

    @Override
    public T visit(DroveExecutorAddedEvent executorAdded) {
        return defaultValue;
    }

    @Override
    public T visit(DroveExecutorRemovedEvent executorRemoved) {
        return defaultValue;
    }

    @Override
    public T visit(DroveExecutorBlacklistedEvent executorBlacklisted) {
        return defaultValue;
    }

    @Override
    public T visit(DroveExecutorUnblacklistedEvent executorUnBlacklisted) {
        return defaultValue;
    }

    @Override
    public T visit(DroveClusterMaintenanceModeSetEvent clusterMaintenanceModeSet) {
        return defaultValue;
    }

    @Override
    public T visit(DroveClusterMaintenanceModeRemovedEvent clusterMaintenanceModeRemoved) {
        return defaultValue;
    }

    @Override
    public T visit(DroveClusterLeadershipAcquiredEvent leadershipAcquired) {
        return defaultValue;
    }

    @Override
    public T visit(DroveClusterLeadershipLostEvent leadershipLost) {
        return defaultValue;
    }

    @Override
    public T visit(DroveLocalServiceInstanceStateChangeEvent droveLocalServiceInstanceStateChangeEvent) {
        return defaultValue;
    }

    @Override
    public T visit(DroveLocalServiceStateChangeEvent droveLocalServiceStateChangeEvent) {
        return defaultValue;
    }
}
