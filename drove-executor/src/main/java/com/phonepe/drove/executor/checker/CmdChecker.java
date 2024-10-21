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

package com.phonepe.drove.executor.checker;

import com.google.common.base.Strings;
import com.phonepe.drove.common.model.DeploymentUnitSpec;
import com.phonepe.drove.executor.statemachine.InstanceActionContext;
import com.phonepe.drove.models.application.CheckResult;
import com.phonepe.drove.models.application.checks.CheckMode;
import com.phonepe.drove.models.application.checks.CmdCheckModeSpec;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import static com.phonepe.drove.executor.utils.DockerUtils.runCommandInContainer;

/**
 *
 */
@Slf4j
public class CmdChecker implements Checker {
    private final CmdCheckModeSpec cmdCheckModeSpec;
    private final InstanceActionContext<? extends DeploymentUnitSpec> context;

    public CmdChecker(CmdCheckModeSpec cmdCheckModeSpec, InstanceActionContext<? extends DeploymentUnitSpec> context) {
        this.cmdCheckModeSpec = cmdCheckModeSpec;
        this.context = context;
    }

    @Override
    public CheckMode mode() {
        return CheckMode.CMD;
    }

    @Override
    public CheckResult call() {
        val containerId = context.getDockerInstanceId();
        if (Strings.isNullOrEmpty(containerId)) {
            return CheckResult.unhealthy("No container id found, maybe container run has not been called?");
        }

        val output = runCommandInContainer(
                containerId, context.getClient(), cmdCheckModeSpec.getCommand());
        log.debug("Command output: {}", output);
        val msg = output.getOutput();
        val exitCode = output.getStatus();
        if (exitCode == 0) {
            return CheckResult.healthy();
        }
        log.error("Command exited with exit code: {}. Output: {}. Error Message (If any): {}",
                  exitCode, msg, output.getErrorMessage());
        return CheckResult.unhealthy("Command exited with exit code: " + exitCode + ". Output: " + msg);
    }

    @Override
    public void close() {
        log.info("Shut down command checker");
    }
}
