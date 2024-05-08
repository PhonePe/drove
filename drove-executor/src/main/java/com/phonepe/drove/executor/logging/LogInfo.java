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
