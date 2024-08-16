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

import lombok.Data;

import java.security.Principal;

/**
 *
 */
@Data
public abstract class DroveUser implements Principal {
    private final DroveUserType userType;
    private final String id;
    private final DroveUserRole role;

    protected DroveUser(DroveUserType userType, String id, DroveUserRole role) {
        this.userType = userType;
        this.id = id;
        this.role = role;
    }

    public abstract <T> T accept(final DroveUserVisitor<T> visitor);

    @Override
    public String getName() {
        return id;
    }
}
