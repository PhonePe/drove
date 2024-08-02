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

package com.phonepe.drove.eventslistener;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Stores offset in memory. If process restarts, this will get reset to 0.
 */
public class DroveEventPollingOffsetInMemoryStore implements DroveEventPollingOffsetStore {
    private final AtomicLong currentOffset = new AtomicLong();

    @Override
    public long getLastOffset() {
        return currentOffset.get();
    }

    @Override
    public void setLastOffset(long offset) {
        currentOffset.set(offset);
    }
}
