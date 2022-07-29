package com.phonepe.drove.auth.core;

import com.phonepe.drove.auth.model.DroveApplicationInstanceInfo;

import java.util.Optional;

/**
 *
 */
public interface ApplicationInstanceTokenManager {
    Optional<String> generate(final DroveApplicationInstanceInfo info);
    Optional<DroveApplicationInstanceInfo> verify(final String token);
}
