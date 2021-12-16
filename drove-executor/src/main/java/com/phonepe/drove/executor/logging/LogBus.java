package com.phonepe.drove.executor.logging;

import com.google.common.base.Strings;
import io.appform.signals.signalhandlers.SignalConsumer;
import io.appform.signals.signals.ConsumingFireForgetSignal;
import lombok.Value;
import lombok.val;

import javax.inject.Singleton;

/**
 *
 */
@Singleton
public class LogBus {
    public enum LogChannel {
        STDOUT,
        STDERR
    }

    @Value
    public static class LogLine {
        String appId;
        String instanceId;
        LogChannel logChannel;
        String log;
    }

    public interface LogListener extends SignalConsumer<LogLine> {
        String id();
    }

    private final ConsumingFireForgetSignal<LogLine> logGenerated = new ConsumingFireForgetSignal<>();

    public void publish(final LogLine logLine) {
        logGenerated.dispatch(logLine);
    }

    public void registerLogHandler(final LogListener listener) {
        if(null == listener) {
            return;
        }
        val id = listener.id();
        if(Strings.isNullOrEmpty(id)) {
            return;
        }
        logGenerated.connect(id, listener);
    }

    public void unregisterLogHandler(final LogListener listener) {
        if(null == listener) {
            return;
        }
        val id = listener.id();
        if(Strings.isNullOrEmpty(id)) {
            return;
        }
        unregisterLogHandler(id);
    }

    public void unregisterLogHandler(final String id) {
        if(Strings.isNullOrEmpty(id)) {
            return;
        }
        logGenerated.disconnect(id);
    }
}
