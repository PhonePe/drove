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

package com.phonepe.drove.models.api;

import com.phonepe.drove.models.events.DroveEventType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Value;

import java.util.Map;

/**
 * Response for events. The last sync time should be sent in the next events call
 */
@Value
@Schema(description = "Summary of cluster events with counts by type")
public class DroveEventsSummary {
    @Schema(description = "Map of event types to their occurrence counts")
    Map<DroveEventType, Long> eventsCount;

    @Schema(description = "Timestamp to use for the next poll request", example = "1698765432100")
    long lastSyncTime;
}
