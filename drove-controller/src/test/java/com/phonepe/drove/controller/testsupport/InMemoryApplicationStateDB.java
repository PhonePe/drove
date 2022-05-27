package com.phonepe.drove.controller.testsupport;

import com.phonepe.drove.controller.statedb.ApplicationStateDB;
import com.phonepe.drove.models.application.ApplicationInfo;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 *
 */
public class InMemoryApplicationStateDB implements ApplicationStateDB {

    private final Map<String, ApplicationInfo> apps = new ConcurrentHashMap<>();

    @Override
    public List<ApplicationInfo> applications(int start, int size) {
        return List.copyOf(apps.values());
    }

    @Override
    public Optional<ApplicationInfo> application(String appId) {
        return Optional.ofNullable(apps.get(appId));
    }

    @Override
    public boolean updateApplicationState(String appId, ApplicationInfo applicationInfo) {
        return apps.compute(appId, (id, old) -> applicationInfo) != null;
    }

    @Override
    public boolean deleteApplicationState(String appId) {
        return apps.remove(appId) != null;
    }
}
