package com.phonepe.drove.auth.core;

import com.phonepe.drove.auth.model.DroveUser;
import com.phonepe.drove.auth.model.DroveUserRole;
import io.dropwizard.auth.Authorizer;
import lombok.extern.slf4j.Slf4j;

/**
 *
 */
@Slf4j
public class DroveAuthorizer implements Authorizer<DroveUser> {

    private final boolean disableReadAuth;

    public DroveAuthorizer(boolean disableReadAuth) {
        this.disableReadAuth = disableReadAuth;
    }

    @Override
    public boolean authorize(DroveUser droveUser, String role) {
        //If the role requested is for read only (not r/w), then if disableReadAuth is set, just allow everyone in
        if (role.equals(DroveUserRole.Values.DROVE_EXTERNAL_READ_ONLY_ROLE) && disableReadAuth) {
            log.trace("Allowed in user {} for read-only role as read auth enforcement is turned off",
                      droveUser.getId());
            return true;
        }
        return droveUser.getRole().getValue().equals(role);
    }
}
