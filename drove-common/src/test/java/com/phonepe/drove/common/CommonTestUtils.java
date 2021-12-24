package com.phonepe.drove.common;

import com.phonepe.drove.common.model.executor.ExecutorAddress;
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
    public void delay(final Duration duration) {
        val wait = duration.toMillis();
        val end = new Date(new Date().getTime() + wait);
        await()
                .pollDelay(Duration.ofSeconds(1))
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
        return new ExecutorAddress("testexec1", "h1", 8080);
    }

    public Set<Integer> set(int max) {
        return set(0, max);
    }

    public Set<Integer> set(int min, int max) {
        return IntStream.rangeClosed(min, max)
                .boxed()
                .collect(Collectors.toUnmodifiableSet());
    }

    public static void delay(final Date endTime) {
        await()
                .timeout(Duration.ofSeconds(15))
                .pollDelay(Duration.ofSeconds(3))
                .until(() -> new Date().after(endTime));
    }
}
