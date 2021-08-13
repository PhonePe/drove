package com.phonepe.drove.models.application;

import lombok.Value;

import javax.validation.constraints.NotEmpty;

/**
 * Description of host directories to be mounted in containers
 */
@Value
public class MountedVolume {
    public enum MountMode {
        READ_WRITE,
        READ_ONLY
    }
    @NotEmpty
    String pathInContainer;
    String pathOnHost;
    MountMode mode;
}
