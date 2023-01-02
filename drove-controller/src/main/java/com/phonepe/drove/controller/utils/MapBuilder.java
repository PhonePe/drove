package com.phonepe.drove.controller.utils;

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
