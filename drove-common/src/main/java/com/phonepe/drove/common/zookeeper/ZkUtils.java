package com.phonepe.drove.common.zookeeper;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.curator.framework.CuratorFramework;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.phonepe.drove.common.CommonUtils.sublist;

/**
 *
 */
@UtilityClass
@Slf4j
public class ZkUtils {
    public static boolean setNodeData(
            CuratorFramework curatorFramework,
            String path,
            ObjectMapper mapper, Object object) {
        try {
            curatorFramework.create()
                    .orSetData()
                    .creatingParentContainersIfNeeded()
                    .forPath(path, mapper.writeValueAsBytes(object));
            return true;
        }
        catch (Exception e) {
            log.error("Error writing node data for " + path, e);
        }

        return false;
    }

    public static <T> List<T> readChildrenNodes(
            CuratorFramework curatorFramework,
            String parentPath,
            int start,
            int size,
            Function<String, T> nodeReader) throws Exception {
        val nodes = curatorFramework.getChildren()
                .forPath(parentPath)
                .stream()
                .map(nodeReader)
                .filter(Objects::nonNull)
                .collect(Collectors.toUnmodifiableList());
        if(nodes.isEmpty()) {
            return nodes;
        }
        return sublist(nodes, start, size);
    }

    public static boolean deleteNode(CuratorFramework curatorFramework, String path) {
        try {
            curatorFramework.delete()
                    .idempotent()
                    .forPath(path);
            return true;
        }
        catch (Exception e) {
            log.error("Error deleting app node: " + path, e);
        }
        return false;
    }

    public static <T> T readNodeData(
            CuratorFramework curatorFramework,
            String path,
            ObjectMapper mapper,
            Class<T> clazz) {
        try {
            return mapper.readValue(curatorFramework.getData().forPath(path), clazz);
        }
        catch (Exception e) {
            log.error("Error reading node data: " + path, e);
        }
        return null;
    }
}
