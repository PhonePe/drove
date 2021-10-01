package com.phonepe.drove.controller.statedb;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.phonepe.drove.common.zookeeper.ZkUtils;
import com.phonepe.drove.models.application.ApplicationInfo;
import com.phonepe.drove.models.instance.InstanceInfo;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.curator.framework.CuratorFramework;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;

/**
 *
 */
@Singleton
@Slf4j
public class ZKApplicationStateDB implements ApplicationStateDB {
    private static final String APPLICATION_STATE_PATH = "/applications";
    private static final String INSTANCE_STATE_PATH = "/instances";
    private final CuratorFramework curatorFramework;
    private final ObjectMapper mapper;

    @Inject
    public ZKApplicationStateDB(CuratorFramework curatorFramework, ObjectMapper mapper) {
        this.curatorFramework = curatorFramework;
        this.mapper = mapper;
    }

    @Override
    @SneakyThrows
    public List<ApplicationInfo> applications(int start, int size) {
        return ZkUtils.readChildrenNodes(curatorFramework,
                                         APPLICATION_STATE_PATH,
                                         start,
                                         size,
                                 path -> ZkUtils.readNodeData(curatorFramework, path, mapper, ApplicationInfo.class));
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

    @Override
    @SneakyThrows
    public List<InstanceInfo> instances(String appId, int start, int size) {
        return ZkUtils.readChildrenNodes(curatorFramework,
                                         appInfoPath(appId),
                                         start,
                                         size,
                                 path -> ZkUtils.readNodeData(curatorFramework, path, mapper, InstanceInfo.class));
    }

    @Override
    public boolean updateInstanceState(
            String appId, String instanceId, InstanceInfo instanceInfo) {
        return ZkUtils.setNodeData(curatorFramework,
                                   instanceInfoPath(appId, instanceInfo.getInstanceId()),
                                   mapper,
                                   instanceInfo);
    }

    @Override
    public boolean deleteInstanceState(String appId, String instanceId) {
        return ZkUtils.deleteNode(curatorFramework, instanceInfoPath(appId, instanceId));
    }

    private String appInfoPath(String appId) {
        return APPLICATION_STATE_PATH + "/" + appId;
    }

    private String instanceInfoPath(String appId, String instanceId) {
        return INSTANCE_STATE_PATH + "/" + appId + "/" + instanceId;
    }

}
