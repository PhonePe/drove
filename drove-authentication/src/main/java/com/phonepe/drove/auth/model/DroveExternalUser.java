package com.phonepe.drove.auth.model;

import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;

/**
 *
 */
@Value
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class DroveExternalUser extends DroveUser {
    Object data;

    public DroveExternalUser(String id, DroveUserRole role, Object data) {
        super(DroveUserType.EXTERNAL, id, role);
        this.data = data;
    }

    @Override
    public <T> T accept(DroveUserVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
