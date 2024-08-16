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

import java.util.LinkedHashMap;
import java.util.Map;

/**
 *
 */
public class MapBuilder<K, V> {
    private final Map<K, V> metadata;

    public MapBuilder() {
        this(new LinkedHashMap<>());
    }

    public MapBuilder(Map<K, V> metadata) {
        this.metadata = metadata;
    }

    public MapBuilder<K, V> put(K key, V value) {
        if(null != value) {
            metadata.put(key, value);
        }
        return this;
    }

    public Map<K,V> build() {
        return Map.copyOf(metadata);
    }
}
