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

package com.phonepe.drove.executor.resourcemgmt.resourceloaders;

import com.google.common.base.Joiner;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
@Slf4j
public class CommandUtils {
    private static final long DEFAULT_TIMEOUT_SECONDS = 10;

    private CommandUtils() {
    }

    /**
     * Checks whether the given {@link IOException} indicates that the command was not found
     * (i.e., the binary does not exist on the system).
     *
     * @param e the exception thrown by {@link ProcessBuilder#start()}
     * @return {@code true} if the error indicates the command was not found
     */
    public static boolean isCommandNotFound(final IOException e) {
        if (e == null) {
            return false;
        }
        if (e.getCause() instanceof IOException) {
            val cause = e.getCause();
            val causeMsg = cause.getMessage();
            if (causeMsg != null && causeMsg.contains("No such file or directory")) {
                return true;
            }
        }
        val msg = e.getMessage();
        return msg != null && msg.contains("error=2");
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
        return parseCommandOutput(commandWithArgs, runWithShell, DEFAULT_TIMEOUT_SECONDS);
    }

    public static List<String> parseCommandOutput(
            final String commandWithArgs,
            final boolean runWithShell,
            final long timeoutSeconds) throws InterruptedException, IOException {
        ExecutorService ioReaderPool = null;
        try {
            String[] cmdArgsArray;
            if (!runWithShell) {
                cmdArgsArray = commandWithArgs.split("[ ]+");
            } else {
                cmdArgsArray = new String[]{"/bin/sh", "-c", commandWithArgs};
            }
            String cmdString = Joiner.on(" ").join(cmdArgsArray);
            log.info("Running the following command: {}", cmdString);
            val process = new ProcessBuilder(cmdArgsArray).start();
            // Drain stdout and stderr concurrently, otherwise waits can be unreliable
            // when one of the process pipes is not consumed.
            ioReaderPool = Executors.newFixedThreadPool(2);
            Future<List<String>> stdoutFuture = ioReaderPool.submit(() -> readAllLines(process.getInputStream()));
            Future<List<String>> stderrFuture = ioReaderPool.submit(() -> readAllLines(process.getErrorStream()));

            boolean hasCompleted = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!hasCompleted) {
                process.destroyForcibly();
                String errMessage = "Command execution took more than %d seconds. [command = %s]"
                        .formatted(timeoutSeconds, cmdString);
                log.error(errMessage);
                throw new IOException(errMessage);
            }

            List<String> lines = stdoutFuture.get();
            List<String> errLines = stderrFuture.get();

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                log.error("Error running command: \n{}", errLines);
                throw new IOException(
                        "Command: '%s' returned: %d".formatted(commandWithArgs, exitCode));
            }
            return lines;
        } catch (ExecutionException e) {
            log.error("Failed to read command output for: [{}]", commandWithArgs, e);
            throw new IOException("Failed to read command output", e);
        } catch (InterruptedException e) {
            log.warn("Command run is interrupted");
            Thread.currentThread().interrupt();
            throw e;
        } finally {
            if (ioReaderPool != null) {
                ioReaderPool.shutdownNow();
            }
        }
    }

    private static List<String> readAllLines(java.io.InputStream stream) throws IOException {
        try (val input = new BufferedReader(new InputStreamReader(stream))) {
            return input.lines().toList();
        }
    }
}
