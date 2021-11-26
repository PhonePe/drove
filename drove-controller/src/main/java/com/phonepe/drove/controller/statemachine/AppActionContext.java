package com.phonepe.drove.controller.statemachine;

import com.phonepe.drove.common.ActionContext;
import com.phonepe.drove.models.application.ApplicationSpec;
import com.phonepe.drove.models.operation.ApplicationOperation;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 *
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class AppActionContext extends ActionContext<ApplicationOperation> {
    private final String appId;
    private final ApplicationSpec applicationSpec;

    private String jobId;
    private String schedulingSessionId;
}
