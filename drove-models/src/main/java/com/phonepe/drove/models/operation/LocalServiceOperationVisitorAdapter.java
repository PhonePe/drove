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

package com.phonepe.drove.models.operation;

import com.phonepe.drove.models.operation.localserviceops.*;

/**
 *
 */
public abstract class LocalServiceOperationVisitorAdapter<T> implements LocalServiceOperationVisitor<T> {
    private final T defaultValue;

    protected LocalServiceOperationVisitorAdapter(T defaultValue) {
        this.defaultValue = defaultValue;
    }

    @Override
    public T visit(LocalServiceCreateOperation localServiceCreateOperation) {
        return defaultValue;
    }

    @Override
    public T visit(LocalServiceAdjustInstancesOperation localServiceAdjustInstancesOperation) {
        return defaultValue;
    }

    @Override
    public T visit(LocalServiceDeactivateOperation localServiceDeactivateOperation) {
        return defaultValue;
    }

    @Override
    public T visit(LocalServiceRestartOperation localServiceRestartOperation) {
        return defaultValue;
    }

    @Override
    public T visit(LocalServiceUpdateInstanceCountOperation localServiceUpdateInstanceCountOperation) {
        return defaultValue;
    }

    @Override
    public T visit(LocalServiceDestroyOperation localServiceDestroyOperation) {
        return defaultValue;
    }

    @Override
    public T visit(LocalServiceActivateOperation localServiceActivateOperation) {
        return defaultValue;
    }

    @Override
    public T visit(LocalServiceReplaceInstancesOperation localServiceReplaceInstancesOperation) {
        return defaultValue;
    }

    @Override
    public T visit(LocalServiceStopInstancesOperation localServiceStopInstancesOperation) {
        return defaultValue;
    }
}
