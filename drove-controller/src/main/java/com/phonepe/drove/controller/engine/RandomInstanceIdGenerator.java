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

package com.phonepe.drove.controller.engine;

import com.phonepe.drove.models.application.ApplicationSpec;
import com.phonepe.drove.models.interfaces.DeploymentSpec;
import com.phonepe.drove.models.interfaces.DeploymentSpecVisitor;
import com.phonepe.drove.models.task.TaskSpec;

import javax.inject.Singleton;
import java.util.UUID;

/**
 *
 */
@Singleton
public class RandomInstanceIdGenerator implements InstanceIdGenerator {
    @Override
    public String generate(DeploymentSpec spec) {
        return spec.accept(new DeploymentSpecVisitor<String>() {
            @Override
            public String visit(ApplicationSpec applicationSpec) {
                return "AI-";
            }

            @Override
            public String visit(TaskSpec taskSpec) {
                return "TI-";
            }
        }) + UUID.randomUUID().toString();
    }
}
