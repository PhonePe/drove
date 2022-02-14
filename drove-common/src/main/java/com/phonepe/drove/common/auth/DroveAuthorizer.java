package com.phonepe.drove.common.auth;

import io.dropwizard.auth.Authorizer;

/**
 *
 */
public class DroveAuthorizer implements Authorizer<DroveUser> {

    @Override
    public boolean authorize(DroveUser droveUser, String role) {
        return droveUser.getRole().getValue().equals(role);
    }
}
