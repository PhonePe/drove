package com.phonepe.drove.controller;

import com.github.benmanes.caffeine.cache.CaffeineSpec;
import com.google.inject.Stage;
import com.phonepe.drove.common.auth.*;
import com.phonepe.drove.controller.ui.HandlebarsViewRenderer;
import io.appform.functionmetrics.FunctionMetricsManager;
import io.dropwizard.Application;
import io.dropwizard.auth.*;
import io.dropwizard.auth.basic.BasicCredentialAuthFilter;
import io.dropwizard.auth.chained.ChainedAuthFilter;
import io.dropwizard.configuration.EnvironmentVariableSubstitutor;
import io.dropwizard.configuration.SubstitutingSourceProvider;
import io.dropwizard.jersey.setup.JerseyEnvironment;
import io.dropwizard.server.AbstractServerFactory;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import io.dropwizard.util.Strings;
import lombok.val;
import org.glassfish.jersey.media.sse.SseFeature;
import org.glassfish.jersey.server.filter.RolesAllowedDynamicFeature;
import ru.vyarus.dropwizard.guice.GuiceBundle;
import ru.vyarus.dropwizard.guice.module.installer.feature.health.HealthCheckInstaller;
import ru.vyarus.guicey.gsp.ServerPagesBundle;

import java.util.ArrayList;
import java.util.Objects;

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
        val jersey = environment.jersey();
        jersey.register(SseFeature.class);
        jersey.getResourceConfig().register(SseFeature.class);
        FunctionMetricsManager.initialize("com.phonepe.drove.controller", environment.metrics());

        setupAuth(appConfig, environment, jersey);
    }

    private void setupAuth(AppConfig appConfig, Environment environment, JerseyEnvironment jersey) {
        val basicAuthConfig = Objects.requireNonNullElse(appConfig.getUserAuth(), BasicAuthConfig.DEFAULT);
        val filters = new ArrayList<AuthFilter<?, DroveUser>>();
        filters.add(new DroveClusterAuthFilter.Builder()
                            .setAuthenticator(new DroveClusterSecretAuthenticator(Objects.requireNonNullElse(appConfig.getClusterAuth(),
                                                                                                             ClusterAuthenticationConfig.DEFAULT)))
                            .setAuthorizer(new DroveAuthorizer())
                            .buildAuthFilter());
        if(basicAuthConfig.isEnabled()) {
            val cacheConfig = Strings.isNullOrEmpty(basicAuthConfig.getCachingPolicy())
                              ? BasicAuthConfig.DEFAULT_CACHE_POLICY
                              : basicAuthConfig.getCachingPolicy();
            filters.add(new BasicCredentialAuthFilter.Builder<DroveUser>()
                                .setAuthenticator(new CachingAuthenticator<>(environment.metrics(),
                                                                             new DroveExternalAuthenticator(basicAuthConfig),
                                                                             CaffeineSpec.parse(cacheConfig)))
                                .setAuthorizer(new CachingAuthorizer<>(environment.metrics(),
                                                                       new DroveAuthorizer(),
                                                                       CaffeineSpec.parse(cacheConfig)))
                                .setPrefix("Basic")
                                .buildAuthFilter());
        }
        else {
            filters.add(new DummyAuthFilter.Builder()
                                .setAuthenticator(new DummyAuthFilter.DummyAuthenticator())
                                .setAuthorizer(new DroveAuthorizer())
                                .buildAuthFilter());
        }
        jersey.register(new AuthDynamicFeature(new ChainedAuthFilter(filters)));
        jersey.register(new AuthValueFactoryProvider.Binder<>(DroveUser.class));
        jersey.register(RolesAllowedDynamicFeature.class);
    }

    public static void main(String[] args) throws Exception {
        new App().run(args);
    }
}
