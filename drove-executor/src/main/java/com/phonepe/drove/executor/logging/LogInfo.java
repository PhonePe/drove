package com.phonepe.drove.executor.logging;

import com.phonepe.drove.executor.AppConfig;
import io.dropwizard.logging.DefaultLoggingFactory;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Value;
import lombok.val;

/**
 *
 */
@Value
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class LogInfo {
    public static final LogInfo DEFAULT = new LogInfo(false, null);

    boolean droveLogging;
    String logPrefix;

    public static LogInfo create(final AppConfig appConfig) {
        val lf = (DefaultLoggingFactory)appConfig.getLoggingFactory();
        val logPath = lf.getAppenders()
                .stream()
                .filter(af -> af instanceof DroveAppenderFactory)
                .map(af -> (DroveAppenderFactory)af)
                .map(DroveAppenderFactory::getLogPath)
                .findFirst()
                .orElse(null);
        return new LogInfo(null != logPath, logPath);
    }

    public String logPathFor(final String appId, final String instanceId) {
        if(!droveLogging) {
            return "";
        }
        return logPrefix + "/" + appId + "/" + instanceId;
    }
}
