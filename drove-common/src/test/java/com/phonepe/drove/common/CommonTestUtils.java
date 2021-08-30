package com.phonepe.drove.common;

import io.dropwizard.util.Duration;
import lombok.experimental.UtilityClass;
import lombok.val;
import org.awaitility.Awaitility;

import java.util.Date;
import java.util.concurrent.TimeUnit;

/**
 *
 */
@UtilityClass
public class CommonTestUtils {
    public void delay(final Duration duration) {
        val wait = duration.toMilliseconds();
        val end = new Date(new Date().getTime() + wait);
        Awaitility.await()
                .pollDelay(java.time.Duration.ofSeconds(1))
                .timeout(wait + 5_000, TimeUnit.SECONDS)
                .until(() -> new Date().after(end));
    }
}
