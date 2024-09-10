package com.phonepe.drove.ignite.discovery;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.phonepe.drove.client.DroveClient;
import com.phonepe.drove.common.CommonTestUtils;
import com.phonepe.drove.ignite.discovery.config.DroveIgniteConfig;
import com.phonepe.drove.models.api.ApiResponse;
import com.phonepe.drove.models.instance.InstanceInfo;
import lombok.SneakyThrows;
import lombok.val;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.github.tomakehurst.wiremock.client.WireMock.badRequest;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static com.phonepe.drove.ignite.discovery.DiscoveryTestUtils.createStubForFailed;
import static com.phonepe.drove.ignite.discovery.DiscoveryTestUtils.createStubForNoPeer;
import static com.phonepe.drove.ignite.discovery.DiscoveryTestUtils.createStubForSingleMemberDiscovery;
import static com.phonepe.drove.ignite.discovery.DiscoveryTestUtils.createStubIOErrorr;
import static org.apache.curator.shaded.com.google.common.base.Stopwatch.createStarted;
import static org.junit.jupiter.api.Assertions.assertTrue;

@WireMockTest
@ExtendWith(SystemStubsExtension.class)
class DroveIgniteInstanceHelperTest {

    @SystemStub
    private EnvironmentVariables environmentVariables = new EnvironmentVariables(Map.of("DROVE_APP_INSTANCE_AUTH_TOKEN", "TestToken"));

    @RegisterExtension
    static WireMockExtension controller1 = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @RegisterExtension
    static WireMockExtension controller2 = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @BeforeEach
    void leaderStub() {
        controller1.stubFor(get(DroveClient.PING_API).willReturn(ok()));
        controller2.stubFor(get(DroveClient.PING_API).willReturn(badRequest()));
    }

    private static final ObjectMapper mapper = new ObjectMapper();

    @AfterEach
    void reset() {
        controller1.resetAll();
        controller2.resetAll();
    }

    @Test
    @SneakyThrows
    void testFindCurrentInstances() {
        createStubForSingleMemberDiscovery(controller1, false);
        val igniteConfig = DroveIgniteConfig.builder()
                .droveEndpoint(endpoint())
                .useAppNameForDiscovery(false)
                .build();
        try (val instanceHelper =  new DroveIgniteInstanceHelper(igniteConfig)) {
            CommonTestUtils.waitUntil(() -> instanceHelper.findCurrentInstances(new DroveClient.BasicResponseHandler()) != null);

            val response = instanceHelper.findCurrentInstances(new DroveClient.BasicResponseHandler());
            Assertions.assertEquals(200, response.statusCode());
            val apiData = mapper.readValue(response.body(),
                    new TypeReference<ApiResponse<List<InstanceInfo>>>() {
                    });
            Assertions.assertEquals(1, apiData.getData().size());
        }
    }

    @Test
    @SneakyThrows
    void testFindCurrentInstancesForApp() {
        createStubForSingleMemberDiscovery(controller1, true);
        val igniteConfig = DroveIgniteConfig.builder()
                .droveEndpoint(endpoint())
                .useAppNameForDiscovery(true)
                .build();
        try (val instanceHelper =  new DroveIgniteInstanceHelper(igniteConfig)) {
            CommonTestUtils.waitUntil(() -> instanceHelper.findCurrentInstances(new DroveClient.BasicResponseHandler()) != null);

            val response = instanceHelper.findCurrentInstances(new DroveClient.BasicResponseHandler());
            Assertions.assertEquals(200, response.statusCode());
            val apiData = mapper.readValue(response.body(),
                    new TypeReference<ApiResponse<List<InstanceInfo>>>() {
                    });
            Assertions.assertEquals(1, apiData.getData().size());
        }
    }

    @Test
    @SneakyThrows
    void testInvalidEndpoint() {
        val igniteConfig = DroveIgniteConfig.builder()
                .droveEndpoint("")
                .useAppNameForDiscovery(false)
                .build();
        val exception = Assertions.assertThrows(RuntimeException.class, () -> {
            try(val dpt = new DroveIgniteInstanceHelper(igniteConfig)) {
            }
        });
        assertTrue(exception instanceof IllegalArgumentException);
    }

    @Test
    @SneakyThrows
    void testFindPeersNoLeader() {
        controller1.stubFor(get(DroveClient.PING_API).willReturn(badRequest()));
        controller2.stubFor(get(DroveClient.PING_API).willReturn(badRequest()));
        val igniteConfig = DroveIgniteConfig.builder()
                .droveEndpoint(endpoint())
                .useAppNameForDiscovery(false)
                .build();
        val stopwatch = createStarted();
        try (val instanceHelper =  new DroveIgniteInstanceHelper(igniteConfig)) {
            val response = instanceHelper.findCurrentInstances(new DroveClient.BasicResponseHandler());
            Assertions.assertNull(response);
            val elapsedTime = stopwatch.elapsed(TimeUnit.SECONDS);
            Assertions.assertTrue(elapsedTime >= 30);
        }
    }

    @Test
    @SneakyThrows
    void testFindPeersNoLeaderWithCustomInterval() {
        controller1.stubFor(get(DroveClient.PING_API).willReturn(badRequest()));
        controller2.stubFor(get(DroveClient.PING_API).willReturn(badRequest()));
        val igniteConfig = DroveIgniteConfig.builder()
                .droveEndpoint(endpoint())
                .useAppNameForDiscovery(false)
                .leaderElectionMaxRetryDuration(Duration.ofSeconds(2))
                .build();
        val stopwatch = createStarted();
        try (val instanceHelper =  new DroveIgniteInstanceHelper(igniteConfig)) {
            val response = instanceHelper.findCurrentInstances(new DroveClient.BasicResponseHandler());
            Assertions.assertNull(response);
            val elapsedTime = stopwatch.elapsed(TimeUnit.SECONDS);
            Assertions.assertTrue(elapsedTime >= 2 && elapsedTime <= 30);
        }
    }

    @Test
    @SneakyThrows
    void testPeerCallBadStatus() {
        createStubForNoPeer(controller1);
        val igniteConfig = DroveIgniteConfig.builder()
                .droveEndpoint(endpoint())
                .useAppNameForDiscovery(false)
                .build();
        try (val instanceHelper =  new DroveIgniteInstanceHelper(igniteConfig)) {
            val response = instanceHelper.findCurrentInstances(new DroveClient.BasicResponseHandler());
            Assertions.assertTrue(response.body().isEmpty());
        }
    }

    @Test
    @SneakyThrows
    void testPeerCallIOError() {
        createStubIOErrorr(controller1);
        val igniteConfig = DroveIgniteConfig.builder()
                .droveEndpoint(endpoint())
                .useAppNameForDiscovery(false)
                .build();
        try (val instanceHelper =  new DroveIgniteInstanceHelper(igniteConfig)) {
            val response = instanceHelper.findCurrentInstances(new DroveClient.BasicResponseHandler());
            Assertions.assertNull(response);
        }
    }

    @Test
    @SneakyThrows
    void testPeerCallFail() {
        createStubForFailed(controller1);
        val igniteConfig = DroveIgniteConfig.builder()
                .droveEndpoint(endpoint())
                .useAppNameForDiscovery(false)
                .build();
        try (val instanceHelper =  new DroveIgniteInstanceHelper(igniteConfig)) {
            val response = instanceHelper.findCurrentInstances(new DroveClient.BasicResponseHandler());
            Assertions.assertEquals(200, response.statusCode());
            val apiData = mapper.readValue(response.body(),
                    new TypeReference<ApiResponse<List<InstanceInfo>>>() {
                    });
            Assertions.assertNull(apiData.getData());
        }
    }

    private static String endpoint() {
        return controller1.baseUrl() + "," + controller2.baseUrl();
    }
}
