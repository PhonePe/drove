package com.phonepe.drove.models.application.executable;

import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;

import javax.validation.constraints.NotEmpty;

/**
 *
 */
@Value
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class DockerCoordinates extends ExecutableCoordinates {
    @NotEmpty
    String url;

    public DockerCoordinates(String url) {
        super(ExecutableType.DOCKER);
        this.url = url;
    }

    @Override
    public <T> T accept(ExecutableTypeVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
