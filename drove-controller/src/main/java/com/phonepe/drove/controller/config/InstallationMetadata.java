/*
 *  Copyright (c) 2025 Original Author(s), PhonePe India Pvt. Ltd.
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

package com.phonepe.drove.controller.config;

import lombok.Value;
import org.hibernate.validator.constraints.URL;

import java.util.Map;

/**
 * Installation specific details about the cluster
 */
@Value
public class InstallationMetadata {
    public static final InstallationMetadata DEFAULT = new InstallationMetadata("DEV_VERSION",
                                                                                new ViewOptions.InstallationConfig(
                                                                                        "local",
                                                                                        ViewOptions.Criticality.DEVELOPMENT),
                                                                                Map.of());
    String version;
    ViewOptions.InstallationConfig installation;
    Map<String, @URL String> links;
}
