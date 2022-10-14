package com.phonepe.drove.models.application.executable;

import io.dropwizard.util.Duration;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

/**
 *
 */
@Value
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Jacksonized
@Builder
public class DockerCoordinates extends ExecutableCoordinates {
    @NotEmpty(message = "- Specify url for container")
    String url;

    @NotNull(message = "- Specify container fetch (docker pull) timeout")
    Duration dockerPullTimeout;

    public DockerCoordinates(String url, Duration dockerPullTimeout) {
        super(ExecutableType.DOCKER);
        this.url = url;
        this.dockerPullTimeout = dockerPullTimeout;
    }

    @Override
    public <T> T accept(ExecutableTypeVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
