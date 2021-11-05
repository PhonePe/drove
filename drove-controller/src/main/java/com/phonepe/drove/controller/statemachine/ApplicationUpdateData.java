package com.phonepe.drove.controller.statemachine;

import com.phonepe.drove.controller.jobexecutor.JobExecutionResult;
import com.phonepe.drove.models.operation.ApplicationOperation;
import lombok.Value;

/**
 *
 */
@Value
public class ApplicationUpdateData {
    ApplicationOperation operation;
    JobExecutionResult<Boolean> executionResult;
}
