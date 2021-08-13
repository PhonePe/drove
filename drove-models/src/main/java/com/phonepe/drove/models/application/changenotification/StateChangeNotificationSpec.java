package com.phonepe.drove.models.application.changenotification;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Data;

/**
 *
 */
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.EXISTING_PROPERTY,
        property = "type")
@JsonSubTypes(
        @JsonSubTypes.Type(name = "CALLBACK", value = URLStateChangeNotificationSpec.class)
)
@Data
public abstract class StateChangeNotificationSpec {
    private final StateChangeNotifierType type;

    public abstract <T> T accept(final StateChangeNotificationSpecVisitor<T> visitor);
}
