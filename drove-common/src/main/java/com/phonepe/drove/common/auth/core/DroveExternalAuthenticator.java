package com.phonepe.drove.common.auth.core;

import com.phonepe.drove.common.auth.config.BasicAuthConfig;
import com.phonepe.drove.common.auth.model.DroveExternalUser;
import com.phonepe.drove.common.auth.model.DroveExternalUserInfo;
import com.phonepe.drove.common.auth.model.DroveUser;
import io.dropwizard.auth.Authenticator;
import io.dropwizard.auth.basic.BasicCredentials;

import java.util.Optional;


/**
 *
 */
public class DroveExternalAuthenticator implements Authenticator<BasicCredentials, DroveUser> {
    private final BasicAuthConfig basicAuthConfig;

    public DroveExternalAuthenticator(BasicAuthConfig basicAuthConfig) {
        this.basicAuthConfig = basicAuthConfig;
    }

    @Override
    public Optional<DroveUser> authenticate(BasicCredentials credentials) {
        if(!basicAuthConfig.isEnabled()) {
            return Optional.of(toDroveUser(BasicAuthConfig.DEFAULT.getUsers().get(0)));
        }
        return basicAuthConfig.getUsers()
                .stream()
                .filter(user -> user.getUsername().equals(credentials.getUsername())
                        && user.getPassword().equals(credentials.getPassword()))
                .map(user -> (DroveUser) toDroveUser(user))
                .findAny();
    }

    private DroveExternalUser toDroveUser(DroveExternalUserInfo user) {
        return new DroveExternalUser(user.getUsername(), user.getRole(), null);
    }
}
