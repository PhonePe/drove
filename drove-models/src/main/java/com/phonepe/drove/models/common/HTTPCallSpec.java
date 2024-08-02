/*
 *  Copyright (c) 2022 Original Author(s), PhonePe India Pvt. Ltd.
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
import lombok.Builder;
import lombok.ToString;
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
    @Mask
    @ToString.Exclude
    String username;
    @Mask
    @ToString.Exclude
    String password;
    @Mask
    @ToString.Exclude
    String authHeader;
    Map<String, String> headers;

    boolean insecure;
}
