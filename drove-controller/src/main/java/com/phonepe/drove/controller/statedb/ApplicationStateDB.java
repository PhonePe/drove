/*
 *  Copyright (c) 2024 Original Author(s), PhonePe India Pvt. Ltd.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

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
