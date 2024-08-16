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

package com.phonepe.drove.auth.model;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import static com.phonepe.drove.auth.model.DroveUserRole.Values.*;

/**
 *
 */
public enum DroveUserRole {

    CLUSTER_NODE(DROVE_CLUSTER_NODE_ROLE),
    DROVE_APPLICATION_INSTANCE(DROVE_APPLICATION_INSTANCE_ROLE),
    EXTERNAL_READ_WRITE(DROVE_EXTERNAL_READ_WRITE_ROLE),
    EXTERNAL_READ_ONLY(DROVE_EXTERNAL_READ_ONLY_ROLE),
    ;

    @Getter
    private final String value;

    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static final class Values {
        public static final String DROVE_CLUSTER_NODE_ROLE = "DROVE_CLUSTER_NODE";
        public static final String DROVE_APPLICATION_INSTANCE_ROLE = "DROVE_APPLICATION_INSTANCE";
        public static final String DROVE_EXTERNAL_READ_WRITE_ROLE = "DROVE_EXTERNAL_READ_WRITE";
        public static final String DROVE_EXTERNAL_READ_ONLY_ROLE = "DROVE_EXTERNAL_READ_ONLY";
    }

    DroveUserRole(String value) {
        this.value = value;
    }
}
