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

package com.phonepe.drove.common;

import com.google.common.base.Strings;
import com.phonepe.drove.common.model.executor.ExecutorAddress;
import com.phonepe.drove.common.net.HttpCaller;
import com.phonepe.drove.models.info.nodedata.NodeTransportType;
import lombok.experimental.UtilityClass;
import lombok.val;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
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
    public static final String APP_IMAGE_NAME = dockerRepoPrefix() + "/perf-test-server-httplib:releases-0.2";
    public static final String TASK_IMAGE_NAME = dockerRepoPrefix() + "/test-task:releases-0.1";

    private static String dockerRepoPrefix() {
        val prefix = System.getProperty("DOCKER_REPO_PREFIX");
        if(Strings.isNullOrEmpty(prefix)) {
            return "ghcr.io/appform-io";
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

    public HttpCaller httpCaller() {
        return new HttpCaller(CommonUtils.createHttpClient(false), CommonUtils.createHttpClient(true));
    }

    public static byte[] base64(final String content) {
        return Base64.getEncoder().encode(content.getBytes(StandardCharsets.UTF_8));
    }
}
