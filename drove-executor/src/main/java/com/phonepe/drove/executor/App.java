package com.phonepe.drove.executor;

import com.google.inject.Stage;
import io.dropwizard.Application;
import io.dropwizard.configuration.EnvironmentVariableSubstitutor;
import io.dropwizard.configuration.SubstitutingSourceProvider;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import ru.vyarus.dropwizard.guice.GuiceBundle;
import ru.vyarus.dropwizard.guice.module.installer.feature.health.HealthCheckInstaller;

import static com.phonepe.drove.common.CommonUtils.configureMapper;

/**
 *
 */
public class App extends Application<AppConfig> {
    @Override
    public void initialize(Bootstrap<AppConfig> bootstrap) {
        super.initialize(bootstrap);
        bootstrap.setConfigurationSourceProvider(
                new SubstitutingSourceProvider(bootstrap.getConfigurationSourceProvider(),
                                               new EnvironmentVariableSubstitutor(true)));

        bootstrap.addBundle(
                GuiceBundle.builder()
                        .enableAutoConfig("com.phonepe.drove.executor.resources",
                                          "com.phonepe.drove.executor.healthcheck",
                                          "com.phonepe.drove.executor.managed",
                                          "com.phonepe.drove.executor.discovery")
                        .modules(new CoreModule())
                        .installers(HealthCheckInstaller.class)
                        .printDiagnosticInfo()
                        .build(Stage.PRODUCTION));
    }

    @Override
    public void run(AppConfig appConfig, Environment environment) throws Exception {
        configureMapper(environment.getObjectMapper());
    }

    public static void main(String[] args) throws Exception {
        new App().run(args);
    }
}
