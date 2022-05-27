package com.phonepe.drove.controller.statedb;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.phonepe.drove.models.application.ApplicationInfo;
import lombok.extern.slf4j.Slf4j;
import org.apache.curator.framework.CuratorFramework;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static com.phonepe.drove.common.zookeeper.ZkUtils.*;

/**
 *
 */
@Slf4j
@Singleton
public class ZkApplicationStateDB implements ApplicationStateDB {
    private static final String APPLICATION_STATE_PATH = "/applications";

    private final CuratorFramework curatorFramework;
    private final ObjectMapper mapper;

    @Inject
    public ZkApplicationStateDB(CuratorFramework curatorFramework, ObjectMapper mapper) {
        this.curatorFramework = curatorFramework;
        this.mapper = mapper;
    }

    @Override
    public List<ApplicationInfo> applications(int start, int size) {
        try {
            return readChildrenNodes(curatorFramework,
                                             APPLICATION_STATE_PATH,
                                             start,
                                             size,
                                             path -> readNodeData(curatorFramework,
                                                                          appInfoPath(path),
                                                                          mapper,
                                                                          ApplicationInfo.class));
        }
        catch (Exception e) {
            log.error("Error reading application list: " + e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    @Override
    public Optional<ApplicationInfo> application(String appId) {
        return Optional.ofNullable(readNodeData(curatorFramework,
                                                appInfoPath(appId),
                                                mapper,
                                                ApplicationInfo.class));
    }

    @Override
    public boolean updateApplicationState(
            String appId, ApplicationInfo applicationInfo) {
        return setNodeData(curatorFramework, appInfoPath(appId), mapper, applicationInfo);
    }


    @Override
    public boolean deleteApplicationState(String appId) {
        return deleteNode(curatorFramework, appInfoPath(appId));
    }

    private static String appInfoPath(String appId) {
        return APPLICATION_STATE_PATH + "/" + appId;
    }

}
