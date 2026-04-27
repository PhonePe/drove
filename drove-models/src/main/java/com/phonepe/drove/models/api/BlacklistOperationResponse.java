/*
 * Copyright 2026 authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.phonepe.drove.models.api;

import java.util.Set;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

/**
 * Response for executor blacklist/unblacklist operations
 */
@Value
@Jacksonized
@Builder
@Schema(description = "Response for executor blacklist or unblacklist operations indicating success and failure details")
public class BlacklistOperationResponse {
    @Schema(description = "Set of executor IDs that were successfully blacklisted/unblacklisted", example = "[\"executor-1\", \"executor-2\"]")
    Set<String> successful;

    @Schema(description = "Set of executor IDs that failed to be blacklisted/unblacklisted", example = "[]")
    Set<String> failed;

    @Schema(description = "Approximate time in milliseconds for the operation to complete", example = "1500")
    long approxCompletionTimeMs;

    @Schema(description = "Human-readable message describing the operation result", example = "Blacklist operation completed successfully")
    String message;
}
