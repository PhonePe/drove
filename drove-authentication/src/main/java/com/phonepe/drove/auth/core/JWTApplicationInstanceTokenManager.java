package com.phonepe.drove.auth.core;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTCreationException;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.phonepe.drove.auth.config.ApplicationAuthConfig;
import com.phonepe.drove.auth.model.DroveApplicationInstanceInfo;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

/**
 *
 */
@Slf4j
@Singleton
public class JWTApplicationInstanceTokenManager implements ApplicationInstanceTokenManager {
    private static final String TOKEN_ISSUER = "drove";
    private static final String DROVE_APP_ID_CLAIM = "droveAppId";
    private static final String DROVE_INSTANCE_ID_CLAIM = "droveInstanceId";
    private static final String DROVE_EXECUTOR_ID_CLAIM = "droveExecutorId";

    private final ApplicationAuthConfig applicationAuthConfig;

    @Inject
    public JWTApplicationInstanceTokenManager(ApplicationAuthConfig applicationAuthConfig) {
        this.applicationAuthConfig = applicationAuthConfig;
    }

    @Override
    public Optional<String> generate(DroveApplicationInstanceInfo info) {
        try {
            val algorithm = algorithm();
            return Optional.of(
                    JWT.create()
                            .withIssuer(TOKEN_ISSUER)
                            .withJWTId(generateJWTId(info))
                            .withAudience("drove-application-instance")
                            .withSubject("drove-controller")
                            .withIssuedAt(new Date())
                            .withClaim(DROVE_APP_ID_CLAIM, info.getAppId())
                            .withClaim(DROVE_INSTANCE_ID_CLAIM, info.getInstanceId())
                            .withClaim(DROVE_EXECUTOR_ID_CLAIM, info.getExecutorId())
                            .sign(algorithm));
        }
        catch (JWTCreationException e) {
            log.error("Error generating token for " + info + ": ", e);
        }
        return Optional.empty();
    }

    @Override
    public Optional<DroveApplicationInstanceInfo> verify(String token) {
        try {
            val algorithm = algorithm(); //use more secure key
            val verifier = JWT.require(algorithm)
                    .withIssuer(TOKEN_ISSUER)
                    .withAudience("drove-application-instance")
                    .withSubject("drove-controller")
                    .build();
            val jwtData = verifier.verify(token);
            val info = new DroveApplicationInstanceInfo(jwtData.getClaim(DROVE_APP_ID_CLAIM).asString(),
                                                        jwtData.getClaim(DROVE_INSTANCE_ID_CLAIM).asString(),
                                                        jwtData.getClaim(DROVE_EXECUTOR_ID_CLAIM).asString());
            if(generateJWTId(info).equals(jwtData.getId())) {
                return Optional.of(info);
            }
            else {
                log.error("Mismatch between id and info for info: {} token: {}", info, token);
            }
        }
        catch (JWTVerificationException e) {
            log.error("Token validation failure for token [" + token + "]: ", e);
        }
        return Optional.empty();
    }

    private Algorithm algorithm() {
        return Algorithm.HMAC256(applicationAuthConfig.getSecret());
    }

    public static String generateJWTId(DroveApplicationInstanceInfo info) {
        val seed = info.getAppId() + ":" + info.getInstanceId() + ":" + info.getExecutorId();
        return UUID.nameUUIDFromBytes(seed.getBytes()).toString();
    }

}
