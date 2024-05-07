package com.phonepe.drove.executor;

import com.google.inject.Stage;
import com.phonepe.drove.auth.config.ClusterAuthenticationConfig;
import com.phonepe.drove.auth.core.DroveAuthorizer;
import com.phonepe.drove.auth.core.DroveClusterSecretAuthenticator;
import com.phonepe.drove.auth.core.DroveUnauthorizedHandler;
import com.phonepe.drove.auth.filters.DroveClusterAuthFilter;
import com.phonepe.drove.auth.model.DroveUser;
import com.phonepe.drove.common.coverageutils.IgnoreInJacocoGeneratedReport;
import io.appform.functionmetrics.FunctionMetricsManager;
import io.dropwizard.Application;
import io.dropwizard.auth.AuthDynamicFeature;
import io.dropwizard.auth.AuthValueFactoryProvider;
import io.dropwizard.configuration.EnvironmentVariableSubstitutor;
import io.dropwizard.configuration.SubstitutingSourceProvider;
import io.dropwizard.server.AbstractServerFactory;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import lombok.val;
import org.glassfish.jersey.server.filter.RolesAllowedDynamicFeature;
import ru.vyarus.dropwizard.guice.GuiceBundle;
import ru.vyarus.dropwizard.guice.module.installer.feature.health.HealthCheckInstaller;

import java.util.Objects;

import static com.phonepe.drove.common.CommonUtils.configureMapper;

/**
 *
 */
@IgnoreInJacocoGeneratedReport
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
                        .modules(new ExecutorCoreModule())
                        .installers(HealthCheckInstaller.class)
                        .printDiagnosticInfo()
                        .build(Stage.PRODUCTION));
    }

    @Override
    public void run(AppConfig appConfig, Environment environment) {
        configureMapper(environment.getObjectMapper());
        ((AbstractServerFactory) appConfig.getServerFactory()).setJerseyRootPath("/apis/*");
        val jersey = environment.jersey();
        FunctionMetricsManager.initialize("com.phonepe.drove.executor", environment.metrics());
        jersey.register(new AuthDynamicFeature(
                        new DroveClusterAuthFilter.Builder()
                                .setAuthenticator(new DroveClusterSecretAuthenticator(Objects.requireNonNullElse(appConfig.getClusterAuth(), ClusterAuthenticationConfig.DEFAULT)))
                                .setAuthorizer(new DroveAuthorizer())
                                .setUnauthorizedHandler(new DroveUnauthorizedHandler())
                                .buildAuthFilter()
                ));
        jersey.register(new AuthValueFactoryProvider.Binder<>(DroveUser.class));
        jersey.register(RolesAllowedDynamicFeature.class);
    }

    public static void main(String[] args) throws Exception {
        new App().run(args);
    }
}
