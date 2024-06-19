package com.phonepe.drove.auth.core;

import com.phonepe.drove.auth.model.DroveUser;
import com.phonepe.drove.auth.model.DroveUserRole;
import io.dropwizard.auth.Authorizer;
import lombok.extern.slf4j.Slf4j;

/**
 * This authorizer bypasses the core authorizer for read checks if read auth checking is disabled
 */
@Slf4j
public class DroveProxyAuthorizer<T extends DroveUser> implements Authorizer<T> {

    private final Authorizer<T> coreAuthorizer;
    private final boolean disableReadAuth;

    public DroveProxyAuthorizer(Authorizer<T> coreAuthorizer, boolean disableReadAuth) {
        this.disableReadAuth = disableReadAuth;
        this.coreAuthorizer = coreAuthorizer;
    }

    @SuppressWarnings("deprecation")
    @Override
    public boolean authorize(T droveUser, String role) {
        //If the role requested is for read only (not r/w), then if disableReadAuth is set, just allow everyone in
        if (role.equals(DroveUserRole.Values.DROVE_EXTERNAL_READ_ONLY_ROLE) && disableReadAuth) {
            log.trace("Allowed in user {} for read-only role as read auth enforcement is turned off",
                      droveUser.getId());
            return true;
        }
        return coreAuthorizer.authorize(droveUser, role);
    }
}
