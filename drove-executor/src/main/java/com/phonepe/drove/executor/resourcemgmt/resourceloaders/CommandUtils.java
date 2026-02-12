package com.phonepe.drove.executor.resourcemgmt.resourceloaders;

import com.google.common.base.Joiner;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;
import java.util.concurrent.TimeUnit;
@Slf4j
public class CommandUtils {
    private CommandUtils() {
    }

    /**
     * Run the given command and returns the output lines
     *
     * @param commandWithArgs - command along with its arguments to run
     * @param runWithShell    - whether to run the command via a shell program
     * @return {@code List<String>} which represents the output lines
     * @throws InterruptedException - when the command execution is interrupted
     * @throws IOException          - when the command exits with an error
     */
    public static List<String> parseCommandOutput(final String commandWithArgs,
                                                  final boolean runWithShell) throws InterruptedException, IOException {
        try {
            String[] cmdArgsArray;
            if (!runWithShell) {
                cmdArgsArray = commandWithArgs.split("[ ]+");
            } else {
                cmdArgsArray = new String[]{"/bin/zsh", "-c", commandWithArgs};
            }
            String cmdString = Joiner.on(" ").join(cmdArgsArray);
            log.info("Running the following command: {}", cmdString);
            val process = new ProcessBuilder(cmdArgsArray).start();
            List<String> lines;
            List<String> errLines;
            // wait for up to 10 seconds for the command to finish

            try (val input = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                lines = input.lines().toList();
            }

            boolean hasCompleted = process.waitFor(10, TimeUnit.SECONDS);
            if (!hasCompleted) {
                String errMessage = "Command execution took more than 10 seconds. [command = %s]".formatted(
                        cmdString);
                log.error(errMessage);
                throw new IOException(errMessage);
            }

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                try (val input = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                    errLines = input.lines().toList();
                }
                log.error("Error running command: \n{}", errLines);
                throw new IOException(
                        "Command: '%s' returned: %d".formatted(commandWithArgs, exitCode));
            }
            return lines;
        } catch (IOException e) {
            log.error("Encountered exception while running command: [{}]", commandWithArgs, e);
            throw e;
        } catch (InterruptedException e) {
            log.warn("Command run is interrupted");
            Thread.currentThread().interrupt();
            throw e;
        }
    }
}
