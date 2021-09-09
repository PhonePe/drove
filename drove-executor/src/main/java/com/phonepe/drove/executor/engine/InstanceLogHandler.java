package com.phonepe.drove.executor.engine;

import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.model.Frame;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

import java.nio.charset.Charset;
import java.util.Map;

/**
 *
 */
@Slf4j
public class InstanceLogHandler extends ResultCallback.Adapter<Frame> {
    private final Map<String, String> mdc;

    public InstanceLogHandler(Map<String, String> mdc) {
        this.mdc = mdc;
    }

    @Override
    public void onNext(Frame object) {
        MDC.setContextMap(mdc);
        switch (object.getStreamType())  {
            case STDOUT:
                log.info(new String(object.getPayload(), Charset.defaultCharset()));
                break;
            case STDERR:
                log.error(new String(object.getPayload(), Charset.defaultCharset()));
                break;
            case STDIN:
            case RAW:
            default:
                break;
        }
        MDC.clear();
    }
}
