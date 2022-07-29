package com.phonepe.drove.executor.logging;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.sift.MDCBasedDiscriminator;
import ch.qos.logback.classic.sift.SiftingAppender;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.FileAppender;
import ch.qos.logback.core.spi.DeferredProcessingAware;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.dropwizard.logging.AbstractAppenderFactory;
import io.dropwizard.logging.FileAppenderFactory;
import io.dropwizard.logging.async.AsyncAppenderFactory;
import io.dropwizard.logging.filter.LevelFilterFactory;
import io.dropwizard.logging.layout.LayoutFactory;
import io.dropwizard.util.DataSize;
import io.dropwizard.validation.MinDataSize;
import lombok.Getter;
import lombok.Setter;
import lombok.val;

import javax.annotation.Nullable;
import javax.validation.constraints.Min;

/**
 *
 */
@JsonTypeName("drove")
@SuppressWarnings("java:S2326")
public class DroveAppenderFactory<E extends DeferredProcessingAware> extends AbstractAppenderFactory<ILoggingEvent> {
    public static final String DEFAULT_LOG_FILE_NAME = "output.log";

    @Nullable
    @Getter
    @Setter
    private String logPath;

    @Getter
    @Setter
    private boolean archive = true;

    @Nullable
    @Getter
    @Setter
    private String archivedLogFileSuffix;

    @Min(0)
    @Getter
    @Setter
    private int archivedFileCount = 5;

    @Nullable
    @Getter
    @Setter
    private DataSize maxFileSize;

    @Nullable
    @Getter
    @Setter
    private DataSize totalSizeCap;

    @MinDataSize(1)
    @Getter
    @Setter
    private DataSize bufferSize = DataSize.bytes(FileAppender.DEFAULT_BUFFER_SIZE);

    @Getter
    @Setter
    private boolean immediateFlush = true;



    @Override
    @SuppressWarnings("java:S1075")
    public Appender<ILoggingEvent> build(
            LoggerContext loggerContext,
            String applicationName,
            LayoutFactory<ILoggingEvent> layoutFactory,
            LevelFilterFactory<ILoggingEvent> levelFilterFactory,
            AsyncAppenderFactory<ILoggingEvent> asyncAppenderFactory) {


        val siftingAppender = new SiftingAppender();
        siftingAppender.setName("sift-appender");
        siftingAppender.setContext(loggerContext);
        siftingAppender.setAppenderFactory((context, discriminatingValue) -> {
            val faf = new FileAppenderFactory<ILoggingEvent>();
            val parts = discriminatingValue.split(":");
            val filePath = (parts.length == 1)
                ? discriminatingValue + ".log"
                : String.format("%s/%s/%s", parts[0], parts[1], DEFAULT_LOG_FILE_NAME);
            val logFilename = logPath + "/" + filePath;
            faf.setCurrentLogFilename(logFilename);
            faf.setArchive(archive);
            faf.setArchivedFileCount(archivedFileCount);
            faf.setArchivedLogFilenamePattern(logFilename + "-" + archivedLogFileSuffix);
            faf.setMaxFileSize(maxFileSize);
            faf.setTotalSizeCap(totalSizeCap);
            faf.setBufferSize(bufferSize);
            faf.setImmediateFlush(immediateFlush);
            //From base
            faf.setThreshold(getThreshold());
            faf.setLogFormat(getLogFormat());
            faf.setTimeZone(getTimeZone());
            faf.setQueueSize(getQueueSize());
            faf.setDiscardingThreshold(getDiscardingThreshold());
            faf.setMessageRate(getMessageRate());
            faf.setIncludeCallerData(isIncludeCallerData());
            faf.setFilterFactories(getFilterFactories());
            return faf.build(loggerContext, "drove", layoutFactory, levelFilterFactory, asyncAppenderFactory);
        });

        val mdcBasedDiscriminator = new MDCBasedDiscriminator();
        mdcBasedDiscriminator.setKey("instanceLogId");
        mdcBasedDiscriminator.setDefaultValue("drove-executor");
        mdcBasedDiscriminator.start();
        siftingAppender.setDiscriminator(mdcBasedDiscriminator);
        siftingAppender.start();
        return wrapAsync(siftingAppender, asyncAppenderFactory);
    }
}
