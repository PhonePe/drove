package com.phonepe.drove.ignite.discovery;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.phonepe.drove.client.DroveClient;
import com.phonepe.drove.models.instance.InstanceInfo;
import com.phonepe.drove.models.instance.InstancePort;
import com.phonepe.drove.models.instance.LocalInstanceInfo;
import lombok.SneakyThrows;
import lombok.val;
import org.apache.ignite.cluster.ClusterNode;
import org.apache.ignite.spi.discovery.tcp.internal.TcpDiscoveryNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.phonepe.drove.ignite.discovery.DiscoveryTestUtils.createStubForMultipleMembers;
import static com.phonepe.drove.ignite.discovery.DiscoveryTestUtils.createStubForSingleMemberDiscovery;
import static com.phonepe.drove.ignite.discovery.DiscoveryTestUtils.getIgniteInstance;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@WireMockTest(httpPort = 8878)
@ExtendWith(SystemStubsExtension.class)
class DiscoveryTest {

    @SystemStub
    private EnvironmentVariables environmentVariables = new EnvironmentVariables(Map.of("DROVE_APP_INSTANCE_AUTH_TOKEN", "TestToken"));

    DiscoveryTest() throws UnknownHostException {
    }

    @BeforeEach
    void leaderStub() {
        stubFor(WireMock.get(DroveClient.PING_API)
                .willReturn(ok()));
    }

    @Test
    @SneakyThrows
    void testSingleMemberDiscovery() {
        val instanceId = UUID.randomUUID().toString();
        environmentVariables.set("DROVE_INSTANCE_ID", instanceId);
        val discoveryPortName = "discovery";
        val commPortName = "comm";
        createStubForSingleMemberDiscovery(discoveryPortName, commPortName, 5051, 5050,
                6061, 6060,
                instanceId);
        val ignite = getIgniteInstance(discoveryPortName, commPortName);
        val topology = ignite.cluster().topology(1);
        assertEquals(1, topology.size());
        validateNodePort(5050, topology);
        ignite.close();
    }

    @Test
    @SneakyThrows
    void testMultiMemberDiscovery() {
        val instanceId1 = UUID.randomUUID().toString();
        val instanceId2 = UUID.randomUUID().toString();
        val discoveryPortName = "discovery";
        val commPortName = "comm";

        createStubForMultipleMembers(InstanceInfo.builder()
                .appId("1_0_0")
                .appName("test_app")
                .instanceId(instanceId1)
                .executorId("ex1")
                .localInfo(LocalInstanceInfo.builder()
                        .hostname(InetAddress.getLocalHost().getHostAddress())
                        .ports(Map.of(discoveryPortName, InstancePort.builder()
                                .hostPort(5050)
                                .containerPort(5050)
                                .build(), commPortName, InstancePort.builder()
                                .hostPort(6060)
                                .containerPort(6060)
                                .build()))
                        .build())
                .build(), InstanceInfo.builder()
                .appId("1_0_0")
                .appName("test_app")
                .instanceId(instanceId2)
                .executorId("ex1")
                .localInfo(LocalInstanceInfo.builder()
                        .hostname(InetAddress.getLocalHost().getHostAddress())
                        .ports(Map.of(discoveryPortName, InstancePort.builder()
                                .hostPort(5051)
                                .containerPort(5051)
                                .build(), commPortName, InstancePort.builder()
                                .hostPort(6061)
                                .containerPort(6061)
                                .build()))
                        .build())
                .build());

        environmentVariables.set("DROVE_INSTANCE_ID", instanceId1);
        val ignite1 = getIgniteInstance(discoveryPortName, commPortName);
        assertEquals(1, ignite1.cluster().topology(1).size());

        environmentVariables.set("DROVE_INSTANCE_ID", instanceId2);
        val ignite2 = getIgniteInstance(discoveryPortName, commPortName);

        val topologyIgnite1 = ignite1.cluster().topology(2);
        val topologyIgnite2 = ignite2.cluster().topology(2);

        assertEquals(2, topologyIgnite1.size());
        assertEquals(2, topologyIgnite2.size());

        validateNodePort(5050, topologyIgnite1);
        validateNodePort(5051, topologyIgnite1);
        validateNodePort(5050, topologyIgnite2);
        validateNodePort(5051, topologyIgnite2);

        ignite1.close();
        ignite2.close();
    }

    private void validateNodePort(final int port,
                                  final Collection<ClusterNode> clusterNodes) {
        assertTrue(clusterNodes.stream().map(clusterNode -> (TcpDiscoveryNode) clusterNode)
                .anyMatch(node -> node.discoveryPort() == port));
    }
}

