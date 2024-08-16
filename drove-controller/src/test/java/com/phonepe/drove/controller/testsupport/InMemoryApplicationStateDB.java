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

package com.phonepe.drove.controller.testsupport;

import com.phonepe.drove.controller.statedb.ApplicationStateDB;
import com.phonepe.drove.models.application.ApplicationInfo;

import javax.inject.Singleton;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 *
 */
@Singleton
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
