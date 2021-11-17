package com.phonepe.drove.controller;

import com.google.inject.Stage;
import com.phonepe.drove.controller.ui.HandlebarsViewRenderer;
import io.dropwizard.Application;
import io.dropwizard.configuration.EnvironmentVariableSubstitutor;
import io.dropwizard.configuration.SubstitutingSourceProvider;
import io.dropwizard.server.AbstractServerFactory;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import ru.vyarus.dropwizard.guice.GuiceBundle;
import ru.vyarus.dropwizard.guice.module.installer.feature.health.HealthCheckInstaller;
import ru.vyarus.guicey.gsp.ServerPagesBundle;

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
                        .enableAutoConfig("com.phonepe.drove.controller.resources",
                                          "com.phonepe.drove.controller.healthcheck",
                                          "com.phonepe.drove.controller.managed",
                                          "com.phonepe.drove.controller.helpers")
                        .modules(new ControllerCoreModule())
                        .installers(HealthCheckInstaller.class)
                        .bundles(ServerPagesBundle.builder()
                                         .addViewRenderers(new HandlebarsViewRenderer())
                                         .build())
                        .bundles(ServerPagesBundle.app("ui", "/assets/", "/")
                                         .mapViews("/ui")
                                         .requireRenderers("handlebars")
                                         .build())
                        .printDiagnosticInfo()
                        .build(Stage.PRODUCTION));
    }

    @Override
    public void run(AppConfig appConfig, Environment environment) throws Exception {
        configureMapper(environment.getObjectMapper());
        ((AbstractServerFactory)appConfig.getServerFactory()).setJerseyRootPath("/apis/*");
    }

    public static void main(String[] args) throws Exception {
        new App().run(args);
    }
}
