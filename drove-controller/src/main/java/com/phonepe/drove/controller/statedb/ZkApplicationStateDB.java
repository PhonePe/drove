package com.phonepe.drove.controller.statedb;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.phonepe.drove.common.zookeeper.ZkUtils;
import com.phonepe.drove.models.application.ApplicationInfo;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.curator.framework.CuratorFramework;
import org.apache.zookeeper.KeeperException;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 *
 */
@Slf4j
@Singleton
public class ZkApplicationStateDB implements ApplicationStateDB {
    private static final String APPLICATION_STATE_PATH = "/applications";
    private static final String INSTANCE_STATE_PATH = "/instances";

    private final CuratorFramework curatorFramework;
    private final ObjectMapper mapper;

    @Inject
    public ZkApplicationStateDB(
            CuratorFramework curatorFramework,
            ObjectMapper mapper) {
        this.curatorFramework = curatorFramework;
        this.mapper = mapper;
    }

    @Override
    @SneakyThrows
    public List<ApplicationInfo> applications(int start, int size) {
        try {
            return ZkUtils.readChildrenNodes(curatorFramework,
                                             APPLICATION_STATE_PATH,
                                             start,
                                             size,
                                             path -> ZkUtils.readNodeData(curatorFramework,
                                                                          appInfoPath(path),
                                                                          mapper,
                                                                          ApplicationInfo.class));
        }
        catch (KeeperException.NoNodeException e) {
            return Collections.emptyList();
        }
    }

    @Override
    public Optional<ApplicationInfo> application(String appId) {
        return Optional.ofNullable(ZkUtils.readNodeData(curatorFramework,
                                                        appInfoPath(appId),
                                                        mapper,
                                                        ApplicationInfo.class));
    }

    @Override
    public boolean updateApplicationState(
            String appId, ApplicationInfo applicationInfo) {
        return ZkUtils.setNodeData(curatorFramework, appInfoPath(appId), mapper, applicationInfo);
    }


    @Override
    public boolean deleteApplicationState(String appId) {
        return ZkUtils.deleteNode(curatorFramework, appInfoPath(appId));
    }

    private static String appInfoPath(String appId) {
        return APPLICATION_STATE_PATH + "/" + appId;
    }

}
