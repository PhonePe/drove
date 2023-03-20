package com.phonepe.drove.eventslistener;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.TextNode;
import com.phonepe.drove.models.events.DroveEvent;
import com.phonepe.drove.models.events.DroveEventType;
import com.phonepe.drove.models.events.events.*;
import lombok.val;

import java.io.IOException;

/**
 * Custom deserializer for Drove Events
 */
public class DroveEventDeserializer extends JsonDeserializer<DroveEvent> {
    private final ObjectMapper mapper;

    public DroveEventDeserializer(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public DroveEvent deserialize(
            JsonParser jsonParser,
            DeserializationContext context) throws IOException {
        val codec = jsonParser.getCodec();
        val eventNode = codec.readTree(jsonParser);
        return switch (DroveEventType.valueOf(((TextNode)eventNode.get("type")).asText())) {

            case APP_STATE_CHANGE  -> mapper.treeToValue(eventNode, DroveAppStateChangeEvent.class);
            case INSTANCE_STATE_CHANGE -> mapper.treeToValue(eventNode, DroveInstanceStateChangeEvent.class);
            case TASK_STATE_CHANGE -> mapper.treeToValue(eventNode, DroveTaskStateChangeEvent.class);
            case EXECUTOR_ADDED, EXECUTOR_REMOVED, EXECUTOR_BLACKLISTED, EXECUTOR_UN_BLACKLISTED ->
                    mapper.treeToValue(eventNode, DroveExecutorEvent.class);
            case MAINTENANCE_MODE_SET, MAINTENANCE_MODE_REMOVED, LEADERSHIP_ACQUIRED, LEADERSHIP_LOST ->
                    mapper.treeToValue(eventNode, DroveClusterEvent.class);
        };
    }
}
