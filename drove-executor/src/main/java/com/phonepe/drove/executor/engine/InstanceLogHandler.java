package com.phonepe.drove.executor.engine;

import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.StreamType;
import com.google.common.base.Strings;
import com.phonepe.drove.executor.logging.LogBus;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.slf4j.MDC;

import java.nio.charset.Charset;
import java.util.Map;

/**
 * Handles stream of docker log messages
 */
@Slf4j
public class InstanceLogHandler extends ResultCallback.Adapter<Frame> {
    private final Map<String, String> mdc;
    private final String appId;
    private final String instanceId;
    private final LogBus logBus;

    public InstanceLogHandler(
            Map<String, String> mdc,
            final String appId,
            final String instanceId,
            final LogBus logBus) {
        this.mdc = mdc;
        this.appId = appId;
        this.instanceId = instanceId;
        this.logBus = logBus;
    }

    @Override
    public void onNext(Frame object) {
        val logLine = null != object.getPayload()
                      ? new String(object.getPayload(), Charset.defaultCharset())
                      : "";
        if(Strings.isNullOrEmpty(logLine)) {
            return;
        }

        logBus.publish(new LogBus.LogLine(appId,
                                          instanceId,
                                          object.getStreamType().equals(StreamType.STDERR)
                                          ? LogBus.LogChannel.STDERR
                                          : LogBus.LogChannel.STDOUT,
                                          logLine));
        if(null != mdc) {
            MDC.setContextMap(mdc);
        }
        switch (object.getStreamType()) {
            case STDOUT -> log.info(logLine.replaceAll("\\n$", ""));
            case STDERR -> log.error(logLine.replaceAll("\\n$", ""));
            case STDIN, RAW -> {
                //Nothing to do here
            }
        }
        MDC.clear();
    }
}
