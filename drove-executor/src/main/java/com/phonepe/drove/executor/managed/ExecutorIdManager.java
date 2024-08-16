/*
 *  Copyright (c) 2024 Original Author(s), PhonePe India Pvt. Ltd.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.phonepe.drove.executor.managed;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.phonepe.drove.common.CommonUtils;
import com.phonepe.drove.executor.ExecutorOptions;
import com.phonepe.drove.models.info.nodedata.NodeTransportType;
import io.appform.signals.signals.ConsumingFireForgetSignal;
import io.dropwizard.lifecycle.Managed;
import io.dropwizard.lifecycle.ServerLifecycleListener;
import io.dropwizard.setup.Environment;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.eclipse.jetty.server.Server;
import ru.vyarus.dropwizard.guice.module.installer.order.Order;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 *
 */
@Singleton
@Order(10)
@Slf4j
public class ExecutorIdManager implements Managed, ServerLifecycleListener {

    @Value
    public static class ExecutorHostInfo {
        int port;
        String hostname;
        NodeTransportType transportType;
        String executorId;
    }

    final int port;

    private final AtomicReference<ExecutorHostInfo> hostInfo = new AtomicReference<>(null);

    private final ExecutorOptions executorOptions;

    private final ConsumingFireForgetSignal<ExecutorHostInfo> hostInfoGenerated = new ConsumingFireForgetSignal<>();

    @Inject
    public ExecutorIdManager(final Environment environment,
                             final ExecutorOptions executorOptions) {
        this(-1, executorOptions);
        environment.lifecycle().addServerLifecycleListener(this);
    }

    @VisibleForTesting
    public ExecutorIdManager(final int initValue,
                             final ExecutorOptions executorOptions) {
        this.port = initValue;
        this.executorOptions = executorOptions;
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
        val localPort = getLocalPort(server);
        val hostname = Strings.isNullOrEmpty(executorOptions.getHostname())
                ? CommonUtils.hostname()
                : executorOptions.getHostname();
        val generatedExecutorId = CommonUtils.executorId(localPort, hostname);
        val cf = server.getConnectors()[0].getConnectionFactory("ssl");
        val localhostInfo = new ExecutorHostInfo(localPort, hostname,
                                            null == cf
                                            ? NodeTransportType.HTTP
                                            :NodeTransportType.HTTPS,
                                            generatedExecutorId);
        this.hostInfo.set(localhostInfo);
        log.info("Executor host info computed to be: {}", localhostInfo);
        hostInfoGenerated.dispatch(localhostInfo); //Notify everyone waiting for this information
    }

    public ConsumingFireForgetSignal<ExecutorHostInfo> onHostInfoGenerated() {
        return hostInfoGenerated;
    }

    public Optional<String> executorId() {
        return Optional.ofNullable(hostInfo.get())
                .map(ExecutorHostInfo::getExecutorId);
    }
}
