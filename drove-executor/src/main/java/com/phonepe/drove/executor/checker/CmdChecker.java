package com.phonepe.drove.executor.checker;

import com.google.common.base.Strings;
import com.phonepe.drove.common.model.ApplicationInstanceSpec;
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
    private final InstanceActionContext<ApplicationInstanceSpec> context;

    public CmdChecker(CmdCheckModeSpec cmdCheckModeSpec, InstanceActionContext<ApplicationInstanceSpec> context) {
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
