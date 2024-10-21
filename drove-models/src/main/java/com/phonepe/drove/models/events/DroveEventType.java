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

package com.phonepe.drove.models.events;

/**
 *
 */
public enum DroveEventType {
    APP_STATE_CHANGE,
    INSTANCE_STATE_CHANGE,
    TASK_STATE_CHANGE,
    LOCAL_SERVICE_STATE_CHANGE,
    LOCAL_SERVICE_INSTANCE_STATE_CHANGE,
    EXECUTOR_ADDED,
    EXECUTOR_REMOVED,
    EXECUTOR_BLACKLISTED,
    EXECUTOR_UN_BLACKLISTED,
    MAINTENANCE_MODE_SET,
    MAINTENANCE_MODE_REMOVED,

    LEADERSHIP_ACQUIRED,
    LEADERSHIP_LOST
}
