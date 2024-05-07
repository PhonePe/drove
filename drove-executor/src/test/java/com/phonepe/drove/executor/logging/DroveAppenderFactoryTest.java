package com.phonepe.drove.executor.logging;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;
import io.dropwizard.logging.async.AsyncLoggingEventAppenderFactory;
import io.dropwizard.logging.filter.ThresholdLevelFilterFactory;
import io.dropwizard.logging.layout.DropwizardLayoutFactory;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 *
 */
class DroveAppenderFactoryTest {

    @Test
    void testBuildAndAppend() {
        val factory = new DroveAppenderFactory<LoggingEvent>();
        factory.setLogPath("/tmp/drove");
        factory.setArchivedLogFileSuffix("%d");
        factory.setArchivedFileCount(3);
        factory.setThreshold("INFO");
        factory.setArchive(true);
        val appender = factory.build(new LoggerContext(),
                                        "drove",
                                        new DropwizardLayoutFactory(),
                                        new ThresholdLevelFilterFactory(),
                                        new AsyncLoggingEventAppenderFactory());
        assertNotNull(appender);
        appender.doAppend(new LoggingEvent());
    }

    @Test
    void testBuildAndAppendCustomPath() {
        val factory = new DroveAppenderFactory<LoggingEvent>();
        factory.setLogPath("/tmp/drove");
        factory.setArchivedLogFileSuffix("%d");
        factory.setArchivedFileCount(3);
        factory.setThreshold("INFO");
        factory.setArchive(true);

        val appender = factory.build(new LoggerContext(),
                                        "drove",
                                        new DropwizardLayoutFactory(),
                                        new ThresholdLevelFilterFactory(),
                                        new AsyncLoggingEventAppenderFactory());
        assertNotNull(appender);
        MDC.setContextMap(Map.of("instanceLogId", "blah:hmmm"));
        appender.doAppend(new LoggingEvent());
    }

}