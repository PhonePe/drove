package com.phonepe.drove.controller.utils;

import com.phonepe.drove.controller.jobexecutor.Job;
import com.phonepe.drove.controller.jobexecutor.JobContext;
import com.phonepe.drove.controller.jobexecutor.JobResponseCombiner;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 *
 */
@Slf4j
@UtilityClass
public class JobUtils {
    public static <T> boolean executeSingleJob(
            JobContext context, final JobResponseCombiner<T> responseCombiner, final Job<T> job) {
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
