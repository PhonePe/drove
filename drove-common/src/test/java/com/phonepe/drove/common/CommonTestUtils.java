package com.phonepe.drove.common;

import com.google.common.base.Strings;
import com.phonepe.drove.common.model.executor.ExecutorAddress;
import com.phonepe.drove.models.info.nodedata.NodeTransportType;
import lombok.experimental.UtilityClass;
import lombok.val;

import java.time.Duration;
import java.util.Date;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.awaitility.Awaitility.await;

/**
 *
 */
@UtilityClass
public class CommonTestUtils {
    public static final String APP_IMAGE_NAME = dockerRepoPrefix() + "/perf-test-server:0.3";
    public static final String TASK_IMAGE_NAME = dockerRepoPrefix() + "/test-task:0.3";

    private static String dockerRepoPrefix() {
        val prefix = System.getProperty("DOCKER_REPO_PREFIX");
        if(Strings.isNullOrEmpty(prefix)) {
            return "docker.io/santanusinha";
        }
        return prefix;
    }

    public void delay(final Duration duration) {
        val wait = duration.toMillis();
        val end = new Date(new Date().getTime() + wait);
        await()
                .pollDelay(Duration.ofMillis(10))
                .timeout(wait + 5_000, TimeUnit.SECONDS)
                .until(() -> new Date().after(end));
    }

    public void waitUntil(final Callable<Boolean> condition) {
        waitUntil(condition, Duration.ofMinutes(3));
    }

    public void waitUntil(final Callable<Boolean> condition, final Duration duration) {
        await()
                .pollDelay(Duration.ofSeconds(1))
                .timeout(duration)
                .until(condition);
    }

    public ExecutorAddress executor() {
        return new ExecutorAddress("testexec1", "h1", 8080, NodeTransportType.HTTP);
    }

    public Set<Integer> set(int max) {
        return set(0, max);
    }

    public Set<Integer> set(int min, int max) {
        return IntStream.rangeClosed(min, max)
                .boxed()
                .collect(Collectors.toUnmodifiableSet());
    }
}
