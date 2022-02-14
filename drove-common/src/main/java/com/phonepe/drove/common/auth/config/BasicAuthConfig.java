package com.phonepe.drove.common.auth.config;

import com.phonepe.drove.common.auth.model.DroveExternalUserInfo;
import com.phonepe.drove.common.auth.model.DroveUserRole;
import lombok.Value;

import javax.validation.constraints.NotEmpty;
import java.util.List;

/**
 *
 */
@Value
public class BasicAuthConfig {
    public static final String DEFAULT_CACHE_POLICY = "maximumSize=500, expireAfterAccess=30m";
    public static final BasicAuthConfig DEFAULT = new BasicAuthConfig(
            false,
            List.of(new DroveExternalUserInfo("default-user",
                                              "default-password",
                                              DroveUserRole.EXTERNAL_READ_WRITE)),
            DEFAULT_CACHE_POLICY);
    boolean enabled;

    @NotEmpty
    List<DroveExternalUserInfo> users;

    String cachingPolicy;
}
