package com.phonepe.drove.models.common;

import io.dropwizard.util.Duration;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;
import org.hibernate.validator.constraints.Range;

import javax.validation.constraints.NotEmpty;
import java.util.Map;
import java.util.Set;

/**
 *
 */
@Value
@Jacksonized
@Builder
public class HTTPCallSpec {
    public static final Set<Integer> DEFAULT_SUCCESS_CODES = Set.of(200);
    public static final Duration DEFAULT_TIMEOUT = Duration.seconds(1);
    public static final String DEFAULT_PATH = "/";
    public static final HTTPVerb DEFAULT_VERB = HTTPVerb.GET;

    /**
     * HTTP/HTTPS etc
     */
    Protocol protocol;

    /**
     * Host or IP to make call to
     */
    @NotEmpty
    String hostname;

    /**
     * Port will be deduced from {@link #protocol} if not defined
     */
    @Range(min = 0, max = 65_535)
    int port;

    /**
     * Api path. Include query params as needed.
     */
    String path;

    /**
     * Get post etc
     */
    HTTPVerb verb;

    /**
     * Set to include response of non-200 calls as valid
     */
    Set<Integer> successCodes;

    /**
     * Any payload to be sent for post/put
     */
    String payload;

    Duration connectionTimeout;
    Duration operationTimeout;
    String username;
    String password;
    String authHeader;
    Map<String, String> headers;

    boolean insecure;
}
