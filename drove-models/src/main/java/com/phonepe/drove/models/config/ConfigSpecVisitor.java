package com.phonepe.drove.models.config;

import com.phonepe.drove.models.config.impl.ControllerHttpFetchConfigSpec;
import com.phonepe.drove.models.config.impl.ExecutorLocalFileConfigSpec;
import com.phonepe.drove.models.config.impl.ExecutorHttpFetchConfigSpec;
import com.phonepe.drove.models.config.impl.InlineConfigSpec;

/**
 * To handle subclass specific behaviour
 */
public interface ConfigSpecVisitor<T> {
    T visit(InlineConfigSpec inlineConfig);

    T visit(ExecutorLocalFileConfigSpec executorFileConfig);

    T visit(ControllerHttpFetchConfigSpec controllerHttpFetchConfig);

    T visit(ExecutorHttpFetchConfigSpec executorHttpFetchConfig);
}
