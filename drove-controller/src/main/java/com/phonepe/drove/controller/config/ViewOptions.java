/*
 *  Copyright (c) 2025 Original Author(s), PhonePe India Pvt. Ltd.
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

package com.phonepe.drove.controller.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;
import org.hibernate.validator.constraints.Length;
import org.hibernate.validator.constraints.URL;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.util.Map;

/**
 * Some extra details for views
 */
@Value
@AllArgsConstructor
@Builder
@Jacksonized
public class ViewOptions {
    public enum Criticality {
        LOCAL,
        DEVELOPMENT,
        INTEGRATION,
        PRODUCTION
    }

    @Value
    public static class InstallationConfig {
        @Length(min = 0, max = 50)
        String label;
        @NotNull
        ViewOptions.Criticality criticality;
    }

    @Valid
    InstallationConfig installation;

    @Valid
    Map<String, @URL String> links;
}
