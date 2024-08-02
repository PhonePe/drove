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

package com.phonepe.drove.models.config;

import com.phonepe.drove.models.config.impl.ControllerHttpFetchConfigSpec;
import com.phonepe.drove.models.config.impl.ExecutorLocalFileConfigSpec;
import com.phonepe.drove.models.config.impl.ExecutorHttpFetchConfigSpec;
import com.phonepe.drove.models.config.impl.InlineConfigSpec;

/**
 *
 */
public abstract class ConfigSpecVisitorAdapter<T> implements ConfigSpecVisitor<T> {
    private final T defaultValue;

    protected ConfigSpecVisitorAdapter() {
        this(null);
    }

    protected ConfigSpecVisitorAdapter(T defaultValue) {
        this.defaultValue = defaultValue;
    }

    @Override
    public T visit(InlineConfigSpec inlineConfig) {
        return defaultValue;
    }

    @Override
    public T visit(ExecutorLocalFileConfigSpec executorFileConfig) {
        return defaultValue;
    }

    @Override
    public T visit(ControllerHttpFetchConfigSpec controllerHttpFetchConfig) {
        return defaultValue;
    }

    @Override
    public T visit(ExecutorHttpFetchConfigSpec executorHttpFetchConfig) {
        return defaultValue;
    }
}
