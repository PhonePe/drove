package org.apache.ignite.spi.discovery.tcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.phonepe.drove.ignite.discovery.DroveIgniteInstanceHelper;
import com.phonepe.drove.ignite.discovery.config.DroveIgniteConfig;
import com.phonepe.drove.models.instance.InstancePort;
import com.phonepe.drove.models.instance.LocalInstanceInfo;
import lombok.SneakyThrows;
import lombok.val;
import org.apache.ignite.lang.IgniteProductVersion;
import org.apache.ignite.spi.discovery.tcp.internal.TcpDiscoveryNode;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.apache.ignite.spi.discovery.tcp.TcpDiscoverySpi.ATTR_EXT_ADDRS;

@ExtendWith(SystemStubsExtension.class)
class DroveIgniteTcpDiscoveryTest {

    private static final String HOST_ADDRESS = "10.2.2.2";
    private static final int HOST_PORT = 5051;

    private static final int CONTAINER_PORT = 5050;

    private DroveIgniteInstanceHelper droveIgniteInstanceHelper;
    private ObjectMapper mapper;

    @BeforeEach
    void setup() {
        this.droveIgniteInstanceHelper = Mockito.mock(DroveIgniteInstanceHelper.class);
        this.mapper = Mockito.mock(ObjectMapper.class);
    }

    @Test
    @SneakyThrows
    void testTcpDiscovery() {
        val config = DroveIgniteConfig.builder()
                .discoveryPortName("discovery")
                .droveEndpoint("localhost")
                .useAppNameForDiscovery(true)
                .build();
        val localInstanceInfo = LocalInstanceInfo.builder()
                .hostname(HOST_ADDRESS)
                .ports(Map.of("discovery", InstancePort.builder()
                        .hostPort(HOST_PORT)
                        .containerPort(CONTAINER_PORT)
                        .build()))
                .build();
        val droveTcpDiscovery = new DroveIgniteTcpDiscovery(config, localInstanceInfo, droveIgniteInstanceHelper,
                mapper);
        Assertions.assertEquals(InetAddress.getLocalHost().getHostAddress(), droveTcpDiscovery.getLocalAddress());

        val addressResolver = droveTcpDiscovery.getAddressResolver();
        Assertions.assertNotNull(addressResolver);

        val extAddress = addressResolver
                .getExternalAddresses(new InetSocketAddress(InetAddress.getLocalHost().getHostAddress(), 5050))
                .stream().findAny().orElse(null);

        Assertions.assertNotNull(extAddress);
        Assertions.assertEquals(new InetSocketAddress(HOST_ADDRESS, HOST_PORT), extAddress);

        val tcpFinder = droveTcpDiscovery.getIpFinder();
        Assertions.assertTrue(tcpFinder instanceof TcpDiscoveryDroveIpFinder);
    }

    @Test
    @SneakyThrows
    void testGetNodeAddresses() {
        val config = DroveIgniteConfig.builder()
                .discoveryPortName("discovery")
                .droveEndpoint("localhost")
                .useAppNameForDiscovery(true)
                .build();
        val localInstanceInfo = LocalInstanceInfo.builder()
                .hostname("localhost")
                .ports(Map.of("discovery", InstancePort.builder()
                        .hostPort(HOST_PORT)
                        .containerPort(CONTAINER_PORT)
                        .build()))
                .build();
        val droveTcpDiscovery = new DroveIgniteTcpDiscovery(config, localInstanceInfo, droveIgniteInstanceHelper,
                mapper);
        val tcpDiscoveryNode = new TcpDiscoveryNode();
        val extAddress = List.of(new InetSocketAddress(HOST_ADDRESS, HOST_PORT));
        tcpDiscoveryNode.setAttributes(Map.of(droveTcpDiscovery.getName() + "." + ATTR_EXT_ADDRS, extAddress));
        val nodeAddresses = droveTcpDiscovery.getNodeAddresses(tcpDiscoveryNode)
                .stream().findFirst().orElse(null);
        Assertions.assertNotNull(nodeAddresses);
        Assertions.assertEquals(extAddress.get(0), nodeAddresses);
    }

    @Test
    @SneakyThrows
    void testGetNodeAddressesWithSameHost() {
        val config = DroveIgniteConfig.builder()
                .discoveryPortName("discovery")
                .droveEndpoint("localhost")
                .useAppNameForDiscovery(true)
                .build();
        val localInstanceInfo = LocalInstanceInfo.builder()
                .hostname("localhost")
                .ports(Map.of("discovery", InstancePort.builder()
                        .hostPort(HOST_PORT)
                        .containerPort(CONTAINER_PORT)
                        .build()))
                .build();
        val droveTcpDiscovery = new DroveIgniteTcpDiscovery(config, localInstanceInfo, droveIgniteInstanceHelper,
                mapper);
        val tcpDiscoveryNode = new TcpDiscoveryNode();
        val extAddress = List.of(new InetSocketAddress(HOST_ADDRESS, HOST_PORT));
        tcpDiscoveryNode.setAttributes(Map.of(droveTcpDiscovery.getName() + "." + ATTR_EXT_ADDRS, extAddress));

        val lastSuccessfulAddress = new InetSocketAddress(HOST_ADDRESS, 10000);
        tcpDiscoveryNode.lastSuccessfulAddress(lastSuccessfulAddress);

        val nodeAddresses = droveTcpDiscovery.getNodeAddresses(tcpDiscoveryNode, true);
        Assertions.assertEquals(2, nodeAddresses.size());

        Assertions.assertTrue(nodeAddresses.stream().anyMatch(nodeAddress -> nodeAddress == lastSuccessfulAddress));
        Assertions.assertTrue(nodeAddresses.stream().anyMatch(nodeAddress -> nodeAddress == extAddress.get(0)));
    }

    @Test
    @SneakyThrows
    void testSetNodeAttributes() {
        val config = DroveIgniteConfig.builder()
                .discoveryPortName("discovery")
                .droveEndpoint("localhost")
                .useAppNameForDiscovery(true)
                .build();
        val localInstanceInfo = LocalInstanceInfo.builder()
                .hostname("localhost")
                .ports(Map.of("discovery", InstancePort.builder()
                        .hostPort(HOST_PORT)
                        .containerPort(CONTAINER_PORT)
                        .build()))
                .build();
        val droveTcpDiscovery = new DroveIgniteTcpDiscovery(config, localInstanceInfo, droveIgniteInstanceHelper,
                mapper);
        val attrs = new HashMap<String, Object>();
        attrs.put("test1", "val1");
        attrs.put("DroveIgniteTcpCommunication.comm.tcp.addrs", "commTest");
        val ver = new IgniteProductVersion();
        droveTcpDiscovery.setNodeAttributes(attrs, ver);

        Assertions.assertEquals(2, attrs.size());
        Assertions.assertEquals(2, droveTcpDiscovery.locNodeAttrs.size());

        Assertions.assertEquals("val1", attrs.get("test1"));
        Assertions.assertEquals("val1", droveTcpDiscovery.locNodeAttrs.get("test1"));

        Assertions.assertTrue(((List<?>) attrs.get("DroveIgniteTcpCommunication.comm.tcp.addrs")).isEmpty());
        Assertions.assertTrue(((List<?>) droveTcpDiscovery.locNodeAttrs.get("DroveIgniteTcpCommunication.comm.tcp.addrs")).isEmpty());

        Assertions.assertEquals(droveTcpDiscovery.locNodeVer, ver);
    }
}
