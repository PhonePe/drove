package com.phonepe.drove.executor;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.phonepe.drove.executor.engine.InstanceEngine;
import io.dropwizard.setup.Environment;

import javax.inject.Singleton;

/**
 *
 */
public class CoreModule extends AbstractModule {

    @Provides
    @Singleton
    public InstanceEngine engine(final Environment environment) {
        return new InstanceEngine(environment.lifecycle()
                                          .executorService("instance-engine")
                                          .minThreads(1)
                                          .maxThreads(1024)
                                          .build());
    }
}
