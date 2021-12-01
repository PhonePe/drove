package com.phonepe.drove.common;

import com.phonepe.drove.common.model.executor.ExecutorAddress;
import lombok.experimental.UtilityClass;
import lombok.val;
import org.awaitility.Awaitility;

import java.time.Duration;
import java.util.Date;
import java.util.concurrent.TimeUnit;

/**
 *
 */
@UtilityClass
public class CommonTestUtils {
    public void delay(final Duration duration) {
        val wait = duration.toMillis();
        val end = new Date(new Date().getTime() + wait);
        Awaitility.await()
                .pollDelay(java.time.Duration.ofSeconds(1))
                .timeout(wait + 5_000, TimeUnit.SECONDS)
                .until(() -> new Date().after(end));
    }

    public ExecutorAddress executor() {
        return new ExecutorAddress("testexec1", "h1", 8080);
    }
}
