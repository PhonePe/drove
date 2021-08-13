package com.phonepe.drove.models.application.checks;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Data;

/**
 *
 */
@JsonTypeInfo(use =  JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(name = "HTTP", value = HTTPCheckModeSpec.class),
        @JsonSubTypes.Type(name = "CMD", value = CmdCheckModeSpec.class),
})
@Data
public abstract class CheckModeSpec {
    private final CheckMode type;

    public abstract <T> T accept(final CheckModeSpecVisitor<T> visitor);
}
