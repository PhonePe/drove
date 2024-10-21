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

package com.phonepe.drove.executor;

import com.google.inject.Injector;
import com.phonepe.drove.common.model.LocalServiceInstanceSpec;
import com.phonepe.drove.executor.model.ExecutorLocalServiceInstanceInfo;
import com.phonepe.drove.executor.statemachine.ExecutorActionBase;
import com.phonepe.drove.executor.statemachine.InstanceActionContext;
import com.phonepe.drove.executor.statemachine.localservice.LocalServiceInstanceActionFactory;
import com.phonepe.drove.models.instance.LocalServiceInstanceState;
import com.phonepe.drove.statemachine.Transition;

/**
 *
 */
public class InjectingLocalServiceInstanceActionFactory implements LocalServiceInstanceActionFactory {
    private final Injector injector;

    public InjectingLocalServiceInstanceActionFactory(Injector injector) {
        this.injector = injector;
    }

    @Override
    public ExecutorActionBase<ExecutorLocalServiceInstanceInfo,
            LocalServiceInstanceState, LocalServiceInstanceSpec> create(Transition<ExecutorLocalServiceInstanceInfo, Void, LocalServiceInstanceState, InstanceActionContext<LocalServiceInstanceSpec>, ExecutorActionBase<ExecutorLocalServiceInstanceInfo, LocalServiceInstanceState, LocalServiceInstanceSpec>> transition) {
        return injector.getInstance(transition.getAction());

    }
}
