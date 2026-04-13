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

package com.phonepe.drove.models.common;

import io.dropwizard.util.Duration;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.ToString;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;
import org.hibernate.validator.constraints.Range;

import javax.validation.constraints.NotEmpty;
import java.util.Map;
import java.util.Set;

/**
 * Specification for making HTTP calls (health checks, config fetching, etc.)
 */
@Value
@Jacksonized
@Builder
@Schema(description = "HTTP call specification for health checks, configuration fetching, and other HTTP-based operations")
public class HTTPCallSpec {
    public static final Set<Integer> DEFAULT_SUCCESS_CODES = Set.of(200);
    public static final Duration DEFAULT_TIMEOUT = Duration.seconds(1);
    public static final String DEFAULT_PATH = "/";
    public static final HTTPVerb DEFAULT_VERB = HTTPVerb.GET;

    /**
     * HTTP/HTTPS etc
     */
    @Schema(description = "Protocol to use for the HTTP call", example = "HTTP")
    Protocol protocol;

    /**
     * Host or IP to make call to
     */
    @NotEmpty
    @Schema(description = "Hostname or IP address to connect to", example = "config-server.internal", requiredMode = Schema.RequiredMode.REQUIRED)
    String hostname;

    /**
     * Port will be deduced from {@link #protocol} if not defined
     */
    @Range(min = 0, max = 65_535)
    @Schema(description = "Port number (defaults to protocol default if not specified)", example = "8080", minimum = "0", maximum = "65535")
    int port;

    /**
     * Api path. Include query params as needed.
     */
    @Schema(description = "URL path including any query parameters", example = "/api/config?env=prod")
    String path;

    /**
     * Get post etc
     */
    @Schema(description = "HTTP method to use", example = "GET")
    HTTPVerb verb;

    /**
     * Set to include response of non-200 calls as valid
     */
    @Schema(description = "HTTP status codes to consider as successful", example = "[200, 201]")
    Set<Integer> successCodes;

    /**
     * Any payload to be sent for post/put
     */
    @Schema(description = "Request body payload for POST/PUT requests")
    String payload;

    @Schema(description = "Connection timeout duration")
    Duration connectionTimeout;

    @Schema(description = "Operation timeout duration")
    Duration operationTimeout;

    @Mask
    @ToString.Exclude
    @Schema(description = "Username for basic authentication", accessMode = Schema.AccessMode.WRITE_ONLY)
    String username;

    @Mask
    @ToString.Exclude
    @Schema(description = "Password for basic authentication", accessMode = Schema.AccessMode.WRITE_ONLY)
    String password;

    @Mask
    @ToString.Exclude
    @Schema(description = "Pre-formatted Authorization header value", accessMode = Schema.AccessMode.WRITE_ONLY)
    String authHeader;

    @Schema(description = "Additional HTTP headers to include in the request")
    Map<String, String> headers;

    @Schema(description = "Whether to skip SSL certificate verification", example = "false")
    boolean insecure;
}
