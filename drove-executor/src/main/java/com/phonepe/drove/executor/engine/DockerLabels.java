package com.phonepe.drove.executor.engine;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 *
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class DockerLabels {
    public static final String DROVE_JOB_TYPE_LABEL = "drove.job.type";
    public static final String DROVE_INSTANCE_ID_LABEL = "drove.instance.id";
    public static final String DROVE_INSTANCE_SPEC_LABEL = "drove.instance.spec";
    public static final String DROVE_INSTANCE_DATA_LABEL = "drove.instance.info";
}
