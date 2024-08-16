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

package com.phonepe.drove.auth.core;

import at.favre.lib.crypto.bcrypt.BCrypt;
import com.phonepe.drove.auth.config.BasicAuthConfig;
import com.phonepe.drove.auth.model.DroveExternalUser;
import com.phonepe.drove.auth.model.DroveExternalUserInfo;
import com.phonepe.drove.auth.model.DroveUser;
import io.dropwizard.auth.Authenticator;
import io.dropwizard.auth.basic.BasicCredentials;

import java.util.Objects;
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
                        && match(basicAuthConfig.getEncoding(), user.getPassword(), credentials.getPassword()))
                .map(user -> (DroveUser) toDroveUser(user))
                .findAny();
    }

    private DroveExternalUser toDroveUser(DroveExternalUserInfo user) {
        return new DroveExternalUser(user.getUsername(), user.getRole(), null);
    }

    private static boolean match(BasicAuthConfig.AuthEncoding encoding,
                                 String secretInFile,
                                 String secretReceived) {
        return switch (Objects.requireNonNullElse(encoding, BasicAuthConfig.AuthEncoding.PLAIN)) {
            case PLAIN -> secretInFile.equals(secretReceived);
            case CRYPT -> BCrypt.verifyer().verify(secretReceived.toCharArray(), secretInFile).verified;
        };
    }
}
