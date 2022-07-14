package com.phonepe.drove.auth.model;

import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;

/**
 * Represents an authenticated cluster controller node
 */
@Value
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class DroveApplicationInstance extends DroveUser {
    DroveApplicationInstanceInfo tokenInfo;

    public DroveApplicationInstance(String id,
                                    DroveApplicationInstanceInfo tokenInfo) {
        super(DroveUserType.APPLICATION_INSTANCE, id, DroveUserRole.DROVE_APPLICATION_INSTANCE);
        this.tokenInfo = tokenInfo;
    }

    @Override
    public <T> T accept(DroveUserVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
