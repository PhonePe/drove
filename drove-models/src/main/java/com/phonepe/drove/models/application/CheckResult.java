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

package com.phonepe.drove.models.application;

import lombok.Value;

/**
 *
 */
@Value
public class CheckResult {
    public enum Status {
        HEALTHY,
        UNHEALTHY,
        STOPPED
    }

    Status status;
    String message;

    public static CheckResult healthy() {
        return new CheckResult(Status.HEALTHY, "");
    }
    public static CheckResult unhealthy(String message) {
        return new CheckResult(Status.UNHEALTHY, message);
    }

    public static CheckResult stopped() {
        return new CheckResult(Status.STOPPED, "");
    }

}
