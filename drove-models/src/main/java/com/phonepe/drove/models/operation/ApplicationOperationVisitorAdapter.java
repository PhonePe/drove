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

package com.phonepe.drove.models.operation;

import com.phonepe.drove.models.operation.ops.*;

/**
 *
 */
public class ApplicationOperationVisitorAdapter<T> implements ApplicationOperationVisitor<T> {
    private final T defaultValue;

    public ApplicationOperationVisitorAdapter(T defaultValue) {
        this.defaultValue = defaultValue;
    }

    @Override
    public T visit(ApplicationCreateOperation create) {
        return defaultValue;
    }

    @Override
    public T visit(ApplicationDestroyOperation destroy) {
        return defaultValue;
    }

    @Override
    public T visit(ApplicationStartInstancesOperation deploy) {
        return defaultValue;
    }

    @Override
    public T visit(ApplicationStopInstancesOperation stopInstances) {
        return defaultValue;
    }

    @Override
    public T visit(ApplicationScaleOperation scale) {
        return defaultValue;
    }

    @Override
    public T visit(ApplicationReplaceInstancesOperation replaceInstances) {
        return defaultValue;
    }

    @Override
    public T visit(ApplicationSuspendOperation suspend) {
        return defaultValue;
    }

    @Override
    public T visit(ApplicationRecoverOperation recover) {
        return defaultValue;
    }
}
