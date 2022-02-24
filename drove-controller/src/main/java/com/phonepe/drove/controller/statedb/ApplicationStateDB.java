package com.phonepe.drove.controller.statedb;

import com.phonepe.drove.models.application.ApplicationInfo;

import java.util.Date;
import java.util.List;
import java.util.Optional;

/**
 *
 */
public interface ApplicationStateDB {
    List<ApplicationInfo> applications(int start, int size);

    Optional<ApplicationInfo> application(String appId);

    boolean updateApplicationState(String appId, final ApplicationInfo applicationInfo);

    default boolean updateInstanceCount(String appId, long instances) {
        return application(appId)
                .map(appInfo -> new ApplicationInfo(appId,
                                                    appInfo.getSpec(),
                                                    instances,
                                                    appInfo.getCreated(),
                                                    new Date()))
                .map(appInfo -> updateApplicationState(appId, appInfo))
                .orElse(false);
    }

    boolean deleteApplicationState(String appId);

}
