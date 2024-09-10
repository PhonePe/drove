package org.apache.ignite.spi.discovery.tcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.phonepe.drove.ignite.discovery.DroveIgniteInstanceHelper;
import com.phonepe.drove.ignite.discovery.config.DroveIgniteConfig;
import lombok.val;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.Mockito;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Optional;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static com.phonepe.drove.ignite.discovery.DiscoveryTestUtils.createStubForSingleMemberDiscovery;
import static org.mockito.ArgumentMatchers.any;

@WireMockTest
class TcpDiscoveryDroveIpFinderTest {

    @RegisterExtension
    static WireMockExtension controller1 = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    private DroveIgniteInstanceHelper droveIgniteInstanceHelper;
    private ObjectMapper mapper;

    @BeforeEach
    void setup() {
        this.droveIgniteInstanceHelper = Mockito.mock(DroveIgniteInstanceHelper.class);
        this.mapper = Mockito.mock(ObjectMapper.class);
    }

    @Test
    void testTcpDiscoveryIpFinder() throws JsonProcessingException {
        createStubForSingleMemberDiscovery(controller1, 5050);
        val config = DroveIgniteConfig.builder()
                .discoveryPortName("discovery")
                .droveEndpoint(controller1.baseUrl())
                .useAppNameForDiscovery(false)
                .build();
        Mockito.doReturn(Optional.of(List.of(InetSocketAddress.createUnresolved("localhost", 1080))))
                .when(droveIgniteInstanceHelper).findCurrentInstances(any());
        val ipFinder = new TcpDiscoveryDroveIpFinder(droveIgniteInstanceHelper, config.getDiscoveryPortName(), mapper);
        val registeredAddresses = ipFinder.getRegisteredAddresses();
        Assertions.assertEquals(1, registeredAddresses.size());
    }
}
