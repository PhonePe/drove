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

package com.phonepe.drove.controller;

import com.phonepe.drove.auth.config.ApplicationAuthConfig;
import com.phonepe.drove.auth.config.BasicAuthConfig;
import com.phonepe.drove.auth.config.ClusterAuthenticationConfig;
import com.phonepe.drove.common.zookeeper.ZkConfig;
import com.phonepe.drove.controller.config.ControllerOptions;
import io.dropwizard.Configuration;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

/**
 *
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class AppConfig extends Configuration {
    @NotNull
    @Valid
    private ZkConfig zookeeper;

    @Valid
    private ControllerOptions options;

    @Valid
    private ClusterAuthenticationConfig clusterAuth;

    @Valid
    private ApplicationAuthConfig instanceAuth;

    @Valid
    private BasicAuthConfig userAuth;
}
