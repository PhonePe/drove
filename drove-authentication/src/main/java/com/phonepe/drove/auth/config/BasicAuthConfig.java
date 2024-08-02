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

package com.phonepe.drove.auth.config;

import com.phonepe.drove.auth.model.DroveExternalUserInfo;
import com.phonepe.drove.auth.model.DroveUserRole;
import lombok.Value;

import javax.validation.constraints.NotEmpty;
import java.util.List;

/**
 *
 */
@Value
public class BasicAuthConfig {
    public static final AuthEncoding DEFAULT_PASSWORD_ENCODING = AuthEncoding.PLAIN;

    public static final String DEFAULT_CACHE_POLICY = "maximumSize=500, expireAfterAccess=30m";
    public static final BasicAuthConfig DEFAULT = new BasicAuthConfig(
            false,
            List.of(new DroveExternalUserInfo("default-user",
                                              "default-password",
                                              DroveUserRole.EXTERNAL_READ_WRITE)),
            AuthEncoding.PLAIN,
            DEFAULT_CACHE_POLICY);
    public enum AuthEncoding {
        PLAIN,
        CRYPT
    }

    boolean enabled;

    @NotEmpty
    List<DroveExternalUserInfo> users;
    AuthEncoding encoding;

    String cachingPolicy;
}
