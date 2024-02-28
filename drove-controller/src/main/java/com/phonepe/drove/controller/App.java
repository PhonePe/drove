package com.phonepe.drove.controller;

import com.github.benmanes.caffeine.cache.CaffeineSpec;
import com.google.common.base.Strings;
import com.google.inject.Injector;
import com.google.inject.Stage;
import com.phonepe.drove.auth.config.ApplicationAuthConfig;
import com.phonepe.drove.auth.config.BasicAuthConfig;
import com.phonepe.drove.auth.config.ClusterAuthenticationConfig;
import com.phonepe.drove.auth.core.*;
import com.phonepe.drove.auth.filters.CompositeAuthFilter;
import com.phonepe.drove.auth.filters.DroveApplicationInstanceAuthFilter;
import com.phonepe.drove.auth.filters.DroveClusterAuthFilter;
import com.phonepe.drove.auth.filters.DummyAuthFilter;
import com.phonepe.drove.auth.model.DroveUser;
import com.phonepe.drove.controller.ui.HandlebarsViewRenderer;
import io.appform.functionmetrics.FunctionMetricsManager;
import io.dropwizard.Application;
import io.dropwizard.auth.*;
import io.dropwizard.auth.basic.BasicCredentialAuthFilter;
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
    private GuiceBundle guiceBundle;

    @Override
    public void initialize(Bootstrap<AppConfig> bootstrap) {
        super.initialize(bootstrap);
        bootstrap.setConfigurationSourceProvider(
                new SubstitutingSourceProvider(bootstrap.getConfigurationSourceProvider(),
                                               new EnvironmentVariableSubstitutor(true)));

        guiceBundle = GuiceBundle.builder()
                .enableAutoConfig("com.phonepe.drove.controller.resources",
                                  "com.phonepe.drove.controller.healthcheck",
                                  "com.phonepe.drove.controller.managed",
                                  "com.phonepe.drove.controller.helpers",
                                  "com.phonepe.drove.controller.errorhandlers",
                                  "com.phonepe.olympus.im.client.exceptions")
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
                .build(Stage.PRODUCTION);
        bootstrap.addBundle(
                guiceBundle);
    }

    @Override
    public void run(AppConfig appConfig, Environment environment) throws Exception {
        configureMapper(environment.getObjectMapper());
        ((AbstractServerFactory) appConfig.getServerFactory()).setJerseyRootPath("/apis/*");
        val jersey = environment.jersey();
        jersey.register(SseFeature.class);
        FunctionMetricsManager.initialize("com.phonepe.drove.controller", environment.metrics());

        setupAuth(appConfig, environment, jersey, guiceBundle.getInjector());
    }

    @SuppressWarnings("java:S3740")
    private void setupAuth(AppConfig appConfig, Environment environment, JerseyEnvironment jersey, Injector injector) {
        val options = Objects.requireNonNullElse(appConfig.getOptions(), ControllerOptions.DEFAULT);
        val disableReadAuth = Objects.requireNonNullElse(options.getDisableReadAuth(), false);
        val basicAuthConfig = Objects.requireNonNullElse(appConfig.getUserAuth(), BasicAuthConfig.DEFAULT);
        val filters = new ArrayList<AuthFilter<?, ? extends DroveUser>>();
        val clusterAuthConfig = Objects.requireNonNullElse(appConfig.getClusterAuth(),
                                                                          ClusterAuthenticationConfig.DEFAULT);
        val applicationAuthConfig = Objects.requireNonNullElse(appConfig.getInstanceAuth(),
                                                               ApplicationAuthConfig.DEFAULT);
        filters.add(new DroveClusterAuthFilter.Builder()
                            .setAuthenticator(new DroveClusterSecretAuthenticator(clusterAuthConfig))
                            .setAuthorizer(new DroveProxyAuthorizer<>(new DroveAuthorizer(), disableReadAuth))
                            .buildAuthFilter());
        filters.add(new DroveApplicationInstanceAuthFilter.Builder()
                            .setAuthenticator(new DroveApplicationInstanceAuthenticator(
                                    new JWTApplicationInstanceTokenManager(applicationAuthConfig)))
                            .setAuthorizer(new DroveProxyAuthorizer<>(new DroveAuthorizer(), disableReadAuth))
                            .buildAuthFilter());
        if (basicAuthConfig.isEnabled()) {
            val cacheConfig = Strings.isNullOrEmpty(basicAuthConfig.getCachingPolicy())
                              ? BasicAuthConfig.DEFAULT_CACHE_POLICY
                              : basicAuthConfig.getCachingPolicy();
            filters.add(new BasicCredentialAuthFilter.Builder<DroveUser>()
                                .setAuthenticator(new CachingAuthenticator<>(environment.metrics(),
                                                                             new DroveExternalAuthenticator(
                                                                                     basicAuthConfig),
                                                                             CaffeineSpec.parse(cacheConfig)))
                                .setAuthorizer(new CachingAuthorizer<>(environment.metrics(),
                                                                       new DroveProxyAuthorizer<>(new DroveAuthorizer(), disableReadAuth),
                                                                       CaffeineSpec.parse(cacheConfig)))
                                .setPrefix("Basic")
                                .buildAuthFilter());
        }
        else {
            filters.add(new DummyAuthFilter.Builder()
                                .setAuthenticator(new DummyAuthFilter.DummyAuthenticator())
                                .setAuthorizer(new DroveProxyAuthorizer<>(new DroveAuthorizer(), disableReadAuth))
                                .buildAuthFilter());
        }
        val controllerOptions = Objects.requireNonNullElse(appConfig.getOptions(), ControllerOptions.DEFAULT);
        jersey.register(new AuthDynamicFeature(
                new CompositeAuthFilter<>(
                        List.copyOf(filters),
                        true,
                        Objects.requireNonNullElse(controllerOptions.getAuditedHttpMethods(),
                                                   ControllerOptions.DEFAULT_AUDITED_METHODS))));
        jersey.register(new AuthValueFactoryProvider.Binder<>(DroveUser.class));
        jersey.register(RolesAllowedDynamicFeature.class);
    }

    public static void main(String[] args) throws Exception {
        new App().run(args);
    }

    private static List<ComponentAuthConfig> getComponentAuthConfigList(OlympusIMClientConfig olympusIMConfig) {
        HashSet<ComponentAuthConfig> componentAuthConfigs = new HashSet<>(olympusIMConfig.getAuthConfig()
                                                                                  .getAdditionalComponentAuthConfigs());
        componentAuthConfigs.add(new ComponentAuthConfig(olympusIMConfig.getAuthConfig()
                                                                 .getComponentId(), olympusIMConfig.getAuthConfig()
                                                                 .getComponentInstanceId()));
        return new ArrayList<>(componentAuthConfigs);
    }
}
