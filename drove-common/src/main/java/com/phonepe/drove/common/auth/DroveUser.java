package com.phonepe.drove.common.auth;

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
