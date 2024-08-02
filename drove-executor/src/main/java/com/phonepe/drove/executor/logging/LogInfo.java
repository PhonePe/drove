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

package com.phonepe.drove.executor.logging;

import com.google.common.base.Strings;
import com.phonepe.drove.executor.AppConfig;
import io.dropwizard.logging.DefaultLoggingFactory;
import lombok.*;

import java.util.Objects;

/**
 *
 */
@Value
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class LogInfo {
    public static final LogInfo DEFAULT = new LogInfo(false, null);

    boolean droveLogging;

    @Getter
    String logPrefix;

    public static LogInfo create(final AppConfig appConfig) {
        val lf = (DefaultLoggingFactory)appConfig.getLoggingFactory();
        val logPath = lf.getAppenders()
                .stream()
                .filter(DroveAppenderFactory.class::isInstance)
                .map(af -> (DroveAppenderFactory)af)
                .map(DroveAppenderFactory::getLogPath)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
        val logPathEmpty = Strings.isNullOrEmpty(logPath);
        return new LogInfo(!logPathEmpty, logPathEmpty ? "" : logPath);
    }

    public String logPathFor(final String appId, final String instanceId) {
        if(!droveLogging) {
            return "";
        }
        return logPrefix + "/" + appId + "/" + instanceId;
    }
}
