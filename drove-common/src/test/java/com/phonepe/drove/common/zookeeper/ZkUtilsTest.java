package com.phonepe.drove.common.zookeeper;

import com.phonepe.drove.common.AbstractTestBase;
import com.phonepe.drove.common.CommonTestUtils;
import lombok.*;
import lombok.extern.jackson.Jacksonized;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.imps.CuratorFrameworkState;
import org.apache.curator.retry.RetryNTimes;
import org.apache.curator.test.TestingCluster;
import org.junit.jupiter.api.Test;

import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;

import static com.phonepe.drove.common.zookeeper.ZkUtils.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 */
class ZkUtilsTest extends AbstractTestBase {

    @Value
    @Jacksonized
    @Builder
    private static class TestData {
        int value;
    }

    @Test
    @SneakyThrows
    void testSingleNodeOps() {
        try (val cluster = new TestingCluster(1)) {
            cluster.start();
            try (val curator = buildTestCurator(cluster)) {
                curator.start();
                curator.blockUntilConnected();
                val instance = cluster.getInstances().stream().findAny().orElse(null);
                assertTrue(setNodeData(curator, "/tnode", MAPPER, new TestData(13)));

                //Kill the server
                cluster.killServer(instance);
                assertNull(readNodeData(curator, "/tnode", MAPPER, TestData.class));
                assertFalse(setNodeData(curator, "/tnode", MAPPER, new TestData(14)));
                assertFalse(deleteNode(curator, "/tnode1")); //Returns false in case of error
                assertFalse(exists(curator, "/tnode"));

                //Restart the server again
                cluster.restartServer(instance);
                CommonTestUtils.waitUntil(() -> curator.getState().equals(CuratorFrameworkState.STARTED));

                assertTrue(exists(curator, "/tnode"));
                assertFalse(exists(curator, "/tnode1"));
                assertEquals(13, readNodeData(curator, "/tnode", MAPPER, TestData.class).getValue());
                assertNull(readNodeData(curator, "/tnode1", MAPPER, TestData.class));
                assertNull(readNodeData(curator, "/tnode", MAPPER, TestData.class, d -> d.getValue() == 10));
                assertNotNull(readNodeData(curator, "/tnode", MAPPER, TestData.class, d -> d.getValue() == 13));
                assertTrue(deleteNode(curator, "/tnode"));
                assertTrue(deleteNode(curator, "/tnode1")); //Returns true even if node path is wrong
                assertFalse(exists(curator, "/tnode"));
                assertNull(readNodeData(curator, "/tnode", MAPPER, TestData.class));


            }
        }
    }

    @Test
    @SneakyThrows
    void testMultiNode() {
        try (val cluster = new TestingCluster(1)) {
            cluster.start();
            try (val curator = buildTestCurator(cluster)) {
                curator.start();
                curator.blockUntilConnected();
                val instance = cluster.getInstances().stream().findAny().orElse(null);
                val input = IntStream.rangeClosed(1, 10)
                        .mapToObj(i -> {
                            val path = "/parent/node_" + i;
                            val data = new TestData(i);
                            assertTrue(setNodeData(curator, path, MAPPER, data));
                            return data;
                        })
                        .sorted(Comparator.comparing(TestData::getValue))
                        .toList();
                //Kill the server
                cluster.killServer(instance);
                assertTrue(readChildren(curator, "/parent").isEmpty());

                //Restart the server again
                cluster.restartServer(instance);
                CommonTestUtils.waitUntil(() -> curator.getState().equals(CuratorFrameworkState.STARTED));
                assertEquals(input, readChildren(curator, "/parent")
                        .stream()
                        .sorted(Comparator.comparing(TestData::getValue))
                        .toList());
                assertTrue(readChildren(curator, "/parent1").isEmpty());
                IntStream.rangeClosed(1, 10)
                        .forEach(i -> assertTrue(deleteNode(curator, "/parent/node_" + i)));
                assertTrue(readChildren(curator, "/parent").isEmpty());
            }
        }
    }

    private List<TestData> readChildren(CuratorFramework curator, String parentPath) throws Exception {
        return readChildrenNodes(curator,
                                 parentPath,
                                 0,
                                 Integer.MAX_VALUE,
                                 childPath -> readNodeData(curator,
                                                           "/parent/" + childPath,
                                                           MAPPER,
                                                           TestData.class));
    }

    private CuratorFramework buildTestCurator(TestingCluster cluster) {
        return CuratorFrameworkFactory.builder()
                .connectString(cluster.getConnectString())
                .namespace("DTEST")
                .retryPolicy(new RetryNTimes(0, 10))
                .sessionTimeoutMs(10)
                .build();
    }


}