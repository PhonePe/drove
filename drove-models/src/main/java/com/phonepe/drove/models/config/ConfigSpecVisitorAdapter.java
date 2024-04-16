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
