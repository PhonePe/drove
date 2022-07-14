package com.phonepe.drove.auth.core;

import com.phonepe.drove.auth.model.DroveApplicationInstance;
import com.phonepe.drove.auth.model.DroveUser;
import io.dropwizard.auth.AuthenticationException;
import io.dropwizard.auth.Authenticator;

import java.util.Optional;

/**
 *
 */
public class DroveApplicationInstanceAuthenticator implements Authenticator<String, DroveUser> {

    private final ApplicationInstanceTokenManager applicationInstanceTokenManager;

    public DroveApplicationInstanceAuthenticator(ApplicationInstanceTokenManager applicationInstanceTokenManager) {
        this.applicationInstanceTokenManager = applicationInstanceTokenManager;
    }

    @Override
    public Optional<DroveUser> authenticate(String credentials) throws AuthenticationException {
        return applicationInstanceTokenManager.verify(credentials)
                .map(info -> new DroveApplicationInstance(info.getAppId() + ":" + info.getInstanceId(), info));
    }
}
