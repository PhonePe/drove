package com.phonepe.drove.executor.engine;

import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.model.Frame;
import com.google.common.base.Strings;
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

    public InstanceLogHandler(Map<String, String> mdc) {
        this.mdc = mdc;
    }

    @Override
    public void onNext(Frame object) {
        val logLine = null != object.getPayload()
                      ? new String(object.getPayload(), Charset.defaultCharset())
                      : "";
        if(Strings.isNullOrEmpty(logLine)) {
            return;
        }

        if(null != mdc) {
            MDC.setContextMap(mdc);
        }
        switch (object.getStreamType()) {
            case STDOUT -> log.info(logLine.replaceAll("\\n$", ""));
            case STDERR -> log.error(logLine.replaceAll("\\n$", ""));
            default -> {
                //Nothing to do here
            }
        }
        MDC.clear();
    }
}
