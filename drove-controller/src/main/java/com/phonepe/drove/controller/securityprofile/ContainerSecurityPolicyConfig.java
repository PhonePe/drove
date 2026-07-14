/*
 * Copyright (c) 2026 Original Author(s), PhonePe India Pvt. Ltd.
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

package com.phonepe.drove.controller.securityprofile;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import javax.validation.constraints.NotEmpty;

import com.google.common.base.Preconditions;
import com.phonepe.drove.models.application.JobType;
import com.phonepe.drove.models.policies.ContainerSecurityProfile;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;
import lombok.With;
import lombok.val;

@Value
@Builder
@With
public class ContainerSecurityPolicyConfig {

    @Value
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class ParsedPolicyRule {
        Set<JobType> applicableTypes;
        Pattern namePattern;

        public static ParsedPolicyRule from(final String matchRule) {
            val parts = matchRule.split("/");
            Preconditions.checkArgument(parts.length > 0 && parts.length <= 2,
                    "Rules are of the format '(jobtype|*)/(*|name regex)");
            //First part should be job type
            //If this is '*', this will be applicable to all jobTypes
            val jobTypes = parseToJobType(parts[0]);
            Preconditions.checkArgument(!jobTypes.isEmpty(), "Could not extract jobType from rules");
            if(parts.length == 1 || parts[1].equals("*")) {
                return new ParsedPolicyRule(jobTypes, Pattern.compile(".*"));
            }
            return new ParsedPolicyRule(jobTypes, Pattern.compile(parts[1]));
        }

        private static Set<JobType> parseToJobType(final String value) {
            if(value.equals("*")) {
                return Set.copyOf(EnumSet.allOf(JobType.class));
            }
            try {
                return Set.of(JobType.valueOf(value));
            }
            catch(Exception e) {
                return Set.of();
            }
        }
    }

    @NotEmpty
    List<@NotEmpty String> match;
    ContainerSecurityProfile profile;
}
