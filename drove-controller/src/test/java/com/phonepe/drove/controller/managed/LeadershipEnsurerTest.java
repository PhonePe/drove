package com.phonepe.drove.controller.managed;

import com.codahale.metrics.SharedMetricRegistries;
import com.phonepe.drove.common.discovery.NodeDataStore;
import com.phonepe.drove.common.zookeeper.ZkConfig;
import com.phonepe.drove.models.info.nodedata.ControllerNodeData;
import com.phonepe.drove.models.info.nodedata.NodeData;
import io.dropwizard.lifecycle.setup.LifecycleEnvironment;
import io.dropwizard.setup.Environment;
import lombok.SneakyThrows;
import lombok.val;
import org.apache.curator.test.TestingCluster;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static com.phonepe.drove.common.CommonUtils.buildCurator;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 *
 */
class LeadershipEnsurerTest {

    @Test
    @SneakyThrows
    void testLeadershipController() {
        try (val cluster = new TestingCluster(1)) {
            cluster.start();
            try (val curator = buildCurator(new ZkConfig().setConnectionString(cluster.getConnectString())
                                                    .setNameSpace("DTEST"))) {
                curator.start();
                curator.blockUntilConnected();
                val nodeStore = mock(NodeDataStore.class);
                val env = mock(Environment.class);
                val lifecycle = new LifecycleEnvironment(SharedMetricRegistries.getOrCreate("test"));
                when(env.lifecycle()).thenReturn(lifecycle);

                val l1 = new LeadershipEnsurer(curator, nodeStore, env);
                val l2 = new LeadershipEnsurer(curator, nodeStore, env);
                val nodeData = new AtomicReference<ControllerNodeData>();
                doAnswer(invocationOnMock -> {
                    val node = invocationOnMock.getArgument(0, ControllerNodeData.class);
                    if(node.isLeader()) {
                        nodeData.set(node);
                    }
                    return null;
                }).when(nodeStore).updateNodeData(any(NodeData.class));
                l1.start();
                l1.serverStarted(server(8080));
                //l2.start();
                await()
                        .atMost(Duration.ofMinutes(1))
                        .until(() -> null != nodeData.get());
                assertTrue(nodeData.get().isLeader());
                assertEquals(8080, nodeData.get().getPort());

                //Check failover
                nodeData.set(null);
                l2.start();
                l2.serverStarted(server(9000));
                l1.stop();
                await()
                        .atMost(Duration.ofMinutes(1))
                        .until(() -> null != nodeData.get());
                assertTrue(nodeData.get().isLeader());
                assertEquals(9000, nodeData.get().getPort());
            }
        }
    }

    private Server server(int port) {
        val server = mock(Server.class);
        val conn = mock(ServerConnector.class);
        when(conn.getLocalPort()).thenReturn(port);
        when(server.getConnectors()).thenReturn(new ServerConnector[] { conn });
        return server;
    }
}