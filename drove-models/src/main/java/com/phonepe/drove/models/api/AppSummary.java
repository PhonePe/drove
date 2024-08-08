/*
 *  Copyright (c) 2022 Original Author(s), PhonePe India Pvt. Ltd.
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

package com.phonepe.drove.models.api;

import com.phonepe.drove.models.application.ApplicationState;
import lombok.Value;

import java.util.Date;
import java.util.Map;

/**
 *
 */
@Value
public class AppSummary {
    String id;
    String name;
    long requiredInstances;
    long healthyInstances;
    long totalCPUs;
    long totalMemory;
    Map<String, String> tags;
    ApplicationState state;
    Date created;
    Date updated;
}
