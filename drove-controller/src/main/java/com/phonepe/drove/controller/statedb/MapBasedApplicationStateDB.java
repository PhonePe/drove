package com.phonepe.drove.controller.statedb;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.phonepe.drove.common.zookeeper.ZkUtils;
import com.phonepe.drove.models.application.ApplicationInfo;
import com.phonepe.drove.models.instance.InstanceInfo;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import lombok.Value;
import org.apache.curator.framework.CuratorFramework;

import javax.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static com.phonepe.drove.common.CommonUtils.sublist;

/**
 *
 */
@Value
@AllArgsConstructor(onConstructor = @__({@Inject}))
public class MapBasedApplicationStateDB implements ApplicationStateDB {
    private static final String APPLICATION_STATE_PATH = "/applications";
    private static final String INSTANCE_STATE_PATH = "/instances";

    CuratorFramework curatorFramework;
    ObjectMapper mapper;
    Map<String, Map<String, InstanceInfo>> apps = new ConcurrentHashMap<>();

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
    public List<InstanceInfo> instances(String appId, int start, int size) {
        //TODO:: THIS IS NOT PERFORMANT IN TERMS OF MEMORY
        return sublist(List.copyOf(apps.get(appId).values()), start, size);
    }

    @Override
    public Optional<InstanceInfo> instance(String appId, String instanceId) {
        return Optional.ofNullable(apps.get(appId).get(instanceId));
    }

    @Override
    public boolean updateInstanceState(
            String appId, String instanceId, InstanceInfo instanceInfo) {
        apps.computeIfPresent(appId,
                              (id, value) -> {
                                  value.compute(instanceId, (iid, oldValue) -> instanceInfo);
                                  return value;
                              });
        return true;
    }

    @Override
    public boolean deleteInstanceState(String appId, String instanceId) {
        apps.computeIfPresent(appId, (id, value) -> {
            value.remove(instanceId);
            return value;
        });
        return true;
    }

    private String appInfoPath(String appId) {
        return APPLICATION_STATE_PATH + "/" + appId;
    }

}
