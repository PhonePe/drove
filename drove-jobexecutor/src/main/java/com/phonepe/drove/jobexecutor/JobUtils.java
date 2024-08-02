/*
 *  Copyright (c) 2022 Original Author(s), PhonePe India Pvt. Ltd.
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

package com.phonepe.drove.jobexecutor;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 *
 */
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class JobUtils {
    public static <T> boolean executeSingleJob(
            JobContext<T> context, final JobResponseCombiner<T> responseCombiner, final Job<T> job) {
        log.info("Calling: {}", job.jobId());
        try {
            if (context.isStopped()) {
                log.warn("Job {} has already been stopped", job.jobId());
            }
            else {
                responseCombiner.combine(job, job.execute(context, responseCombiner));
                log.debug("Job {} completed successfully", job.jobId());
                return true;
            }
        }
        catch (Exception e) {
            if (responseCombiner.handleError(e)) {
                log.debug("Job " + job.jobId() + " failed with error: " + e.getMessage(), e);
            }
            else {
                log.debug("Job " + job.jobId() + " failed with error: " + e.getMessage()
                                  + ". Subsequent executions to be skipped.", e);
            }
            context.markStopped();
        }
        return false;
    }

    public static<T> String idFromChildren(List<Job<T>> jobs) {
        return UUID.nameUUIDFromBytes(
                        jobs.stream().map(Job::jobId).collect(Collectors.joining()).getBytes(StandardCharsets.UTF_8))
                .toString();
    }
}
