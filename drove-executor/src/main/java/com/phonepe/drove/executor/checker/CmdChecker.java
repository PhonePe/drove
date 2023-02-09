package com.phonepe.drove.executor.checker;

import com.github.dockerjava.api.async.ResultCallbackTemplate;
import com.github.dockerjava.api.model.Frame;
import com.google.common.base.Strings;
import com.phonepe.drove.common.coverageutils.IgnoreInJacocoGeneratedReport;
import com.phonepe.drove.common.model.ApplicationInstanceSpec;
import com.phonepe.drove.executor.statemachine.InstanceActionContext;
import com.phonepe.drove.models.application.CheckResult;
import com.phonepe.drove.models.application.checks.CheckMode;
import com.phonepe.drove.models.application.checks.CmdCheckModeSpec;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import java.util.Optional;

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
    public CheckResult call() throws Exception {
        val containerId = context.getDockerInstanceId();
        if (Strings.isNullOrEmpty(containerId)) {
            return CheckResult.unhealthy("No container id found, maybe container run has not been called?");
        }
        val client = context.getClient();
        val execId = client.execCreateCmd(containerId)
                .withAttachStderr(true)
                .withAttachStdout(true)
                .withCmd("sh", "-c", cmdCheckModeSpec.getCommand())
                .exec()
                .getId();
        val callback =
        client.execStartCmd(execId)
                .exec(new CmdExecResultCallback())
                .awaitCompletion();
        val msg = callback.getBuffer().toString();
        log.debug("Command output: {}", msg);
        return callback.result()
                .orElseGet(() -> {
                    val exitCode = client.inspectExecCmd(execId)
                            .exec()
                            .getExitCodeLong();
                    if (exitCode != 0) {
                        log.error("Command exited with exit code: {}. Output: {}", exitCode, msg);
                        return CheckResult.unhealthy("Command exited with exit code: " + exitCode + ". Output: " + msg);
                    }
                    return CheckResult.healthy();
                });
    }

    @Override
    public void close() throws Exception {
        log.info("Shut down command checker");
    }

    private static final class CmdExecResultCallback extends ResultCallbackTemplate<CmdExecResultCallback, Frame> {
        @Getter
        private final StringBuffer buffer = new StringBuffer();

        private CheckResult result = null;

        @Override
        public void onNext(Frame frame) {
            switch (frame.getStreamType()) {
                case STDOUT, STDERR, RAW -> buffer.append(new String(frame.getPayload()));
                case STDIN -> log.error("Received frame of unsupported stream type: {}", frame.getStreamType());
                default -> log.error("Unexpected stream type value: " + frame.getStreamType());
            }
        }

        @Override
        @IgnoreInJacocoGeneratedReport
        public void onError(Throwable throwable) {
            log.error("Error executing command: " + throwable.getMessage(), throwable);
            result = CheckResult.unhealthy("Error executing command: " + throwable.getMessage());
            super.onError(throwable);
        }

        public Optional<CheckResult> result() {
            return Optional.ofNullable(result);
        }
    }
}
