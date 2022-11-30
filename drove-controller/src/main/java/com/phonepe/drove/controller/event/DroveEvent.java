package com.phonepe.drove.controller.event;

import com.phonepe.drove.controller.utils.MapBuilder;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Date;
import java.util.Map;
import java.util.UUID;

/**
 *
 */
@Data
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class DroveEvent<T extends Enum<T>> {
    private final DroveEventType type;
    private final String id = UUID.randomUUID().toString();
    private final Date time = new Date();
    private Map<T, Object> metadata;

    public static<T> MapBuilder<T, Object> metadataBuilder() {
        return new MapBuilder<>();
    }
}
