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

package com.phonepe.drove.executor.engine;

import com.codahale.metrics.MetricRegistry;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.model.Frame;
import com.google.common.base.Strings;
import com.phonepe.drove.executor.model.*;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.slf4j.MDC;

import java.nio.charset.Charset;
import java.util.Map;

import static com.codahale.metrics.MetricRegistry.name;

/**
 * Handles stream of docker log messages
 */
@Slf4j
public class InstanceLogHandler extends ResultCallback.Adapter<Frame> {
    private final Map<String, String> mdc;
    private final DeployedExecutionObjectInfo instanceInfo;
    private final MetricRegistry metricRegistry;

    public InstanceLogHandler(Map<String, String> mdc, DeployedExecutionObjectInfo instanceInfo, MetricRegistry metricRegistry) {
        this.mdc = mdc;
        this.instanceInfo = instanceInfo;
        this.metricRegistry = metricRegistry;
    }

    @Override
    public void onNext(Frame object) {
        val logLine = null != object.getPayload()
                      ? new String(object.getPayload(), Charset.defaultCharset())
                      : "";
        if (Strings.isNullOrEmpty(logLine)) {
            return;
        }

        if (null != mdc) {
            MDC.setContextMap(mdc);
        }
        metricRegistry.meter(metricName(instanceInfo, "logs_rate")).mark();
        metricRegistry.meter(metricName(instanceInfo, "logs_size_bytes"))
                .mark(logLine.getBytes(Charset.defaultCharset()).length);
        switch (object.getStreamType()) {
            case STDOUT -> log.info(logLine.replaceAll("\\n$", ""));
            case STDERR -> log.error(logLine.replaceAll("\\n$", ""));
            default -> {
                //Nothing to do here
            }
        }
        MDC.clear();
    }

    private static String metricName(final DeployedExecutionObjectInfo instanceInfo, String name) {
        return instanceInfo.accept(new DeployedExecutorInstanceInfoVisitor<String>() {
            @Override
            public String visit(ExecutorInstanceInfo applicationInstanceInfo) {
                return metricNameForApp(applicationInstanceInfo, name);
            }

            @Override
            public String visit(ExecutorTaskInfo taskInfo) {
                return metricNameForTasks(taskInfo, name);
            }

            @Override
            public String visit(ExecutorLocalServiceInstanceInfo localServiceInstanceInfo) {
                return metricNameForLocalService(localServiceInstanceInfo, name);
            }
        });
    }

    private static String metricNameForTasks(final ExecutorTaskInfo instanceInfo, String name) {
        return name("com",
                    "phonepe",
                    "drove",
                    "executor",
                    "tasks",
                    instanceInfo.getSourceAppName(),
                    name);
    }

    private static String metricNameForApp(final ExecutorInstanceInfo instanceInfo, String name) {
        return name("com",
                    "phonepe",
                    "drove",
                    "executor",
                    "applications",
                    instanceInfo.getAppName(),
                    "instance",
                    instanceInfo.getInstanceId(),
                    name);
    }

    private static String metricNameForLocalService(final ExecutorLocalServiceInstanceInfo instanceInfo, String name) {
        return name("com",
                    "phonepe",
                    "drove",
                    "executor",
                    "localservices",
                    instanceInfo.getServiceName(),
                    "instance",
                    instanceInfo.getInstanceId(),
                    name);
    }
}
