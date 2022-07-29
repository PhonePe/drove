package com.phonepe.drove.executor.managed;

import com.google.common.annotations.VisibleForTesting;
import com.phonepe.drove.common.CommonUtils;
import io.dropwizard.lifecycle.Managed;
import io.dropwizard.lifecycle.ServerLifecycleListener;
import io.dropwizard.setup.Environment;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jetty.server.Server;
import ru.vyarus.dropwizard.guice.module.installer.order.Order;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/**
 *
 */
@Singleton
@Order(10)
@Slf4j
public class ExecutorIdManager implements Managed, ServerLifecycleListener {
    private final AtomicInteger port;

    @Inject
    public ExecutorIdManager(final Environment environment) {
        this(-1);
        environment.lifecycle().addServerLifecycleListener(this);
    }

    @VisibleForTesting
    public ExecutorIdManager(final int initValue) {
        this.port = new AtomicInteger(initValue);
    }

    @Override
    public void start() throws Exception {
        log.info("Executor ID manager started");
    }

    @Override
    public void stop() throws Exception {
        log.info("Executor ID manager stopped");
    }

    @Override
    public void serverStarted(Server server) {
        port.compareAndSet(-1, getLocalPort(server));
    }

    public Optional<String> executorId() {
        final int localPort = port.get();
        return -1 == localPort
               ? Optional.empty()
               : Optional.of(CommonUtils.executorId(localPort));
    }
}
