package com.phonepe.drove.executor.logging;

import com.google.common.base.Strings;
import com.google.common.eventbus.AsyncEventBus;
import com.google.common.eventbus.Subscribe;
import lombok.Value;
import lombok.val;

import javax.inject.Singleton;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

/**
 *
 */
@Singleton
@SuppressWarnings("UnstableApiUsage")
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

    public interface LogListener {
        String id();

        @Subscribe
        void handle(final LogLine logLine);
    }

    private final Map<String, LogListener> listeners = new ConcurrentHashMap<>();
    private final AsyncEventBus logGenerated = new AsyncEventBus(Executors.newCachedThreadPool());

    public void publish(final LogLine logLine) {
        logGenerated.post(logLine);
    }

    public void registerLogHandler(final LogListener listener) {
        val id = listener.id();
        if(Strings.isNullOrEmpty(id)) {
            return;
        }
        listeners.computeIfAbsent(id, lid -> {
            logGenerated.register(listener);
            return listener;
        });
    }

    public void unregisterLogHandler(final LogListener listener) {
        val id = listener.id();
        if(Strings.isNullOrEmpty(id)) {
            return;
        }
        unregisterLogHandler(id);
    }

    public void unregisterLogHandler(final String id) {
        listeners.computeIfPresent(id, (lid, listener) -> {
            logGenerated.unregister(listener);
            return null;
        });
    }
}
