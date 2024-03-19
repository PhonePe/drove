package com.phonepe.drove.auth.config;

import com.phonepe.drove.auth.model.DroveExternalUserInfo;
import com.phonepe.drove.auth.model.DroveUserRole;
import lombok.Value;

import javax.validation.constraints.NotEmpty;
import java.util.List;

/**
 *
 */
@Value
public class BasicAuthConfig {
    public static final AuthEncoding DEFAULT_PASSWORD_ENCODING = AuthEncoding.PLAIN;

    public static final String DEFAULT_CACHE_POLICY = "maximumSize=500, expireAfterAccess=30m";
    public static final BasicAuthConfig DEFAULT = new BasicAuthConfig(
            false,
            List.of(new DroveExternalUserInfo("default-user",
                                              "default-password",
                                              DroveUserRole.EXTERNAL_READ_WRITE)),
            AuthEncoding.PLAIN,
            DEFAULT_CACHE_POLICY);
    public enum AuthEncoding {
        PLAIN,
        CRYPT
    }

    boolean enabled;

    @NotEmpty
    List<DroveExternalUserInfo> users;
    AuthEncoding encoding;

    String cachingPolicy;
}
