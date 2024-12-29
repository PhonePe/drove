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

package com.phonepe.drove.executor.discovery;

/**
 * Exception to indicate issue while communicating with controller
 */
public class ControllerCommunicationError extends RuntimeException {
    public ControllerCommunicationError(String message) {
        super(message);
    }

    public ControllerCommunicationError(String message, Throwable cause) {
        super(message, cause);
    }

    public static ControllerCommunicationError noLeader() {
        throw new ControllerCommunicationError("Leader not found for cluster. Cannot fetch last state data from controller.");
    }

    public static ControllerCommunicationError commError(Throwable t) {
        throw new ControllerCommunicationError("Error communicating with controller: " + t.getMessage(), t);
    }

    public static ControllerCommunicationError commError(int statusCode, String error) {
        throw new ControllerCommunicationError("Error fetching resources for local service instances. Received response: [%d] %s".formatted(statusCode, error));
    }
}
