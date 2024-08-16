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

package com.phonepe.drove.models.config;

/**
 * Source of configuration for the application or task instance
 */
public enum ConfigSpecType {
    // Sent as part of spec
    INLINE,
    // Locally mounted file
    EXECUTOR_LOCAL_FILE,
    // Config is fetched by controller using http call and sent to instance
    CONTROLLER_HTTP_FETCH,
    // Config is fetched by executor directly by making http call
    EXECUTOR_HTTP_FETCH
}
