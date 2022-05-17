package com.phonepe.drove.models.application;

import lombok.Value;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

/**
 * Description of host directories to be mounted in containers
 */
@Value
public class MountedVolume {
    public enum MountMode {
        READ_WRITE,
        READ_ONLY
    }
    @NotEmpty(message = "- Provide mount path inside container")
    String pathInContainer;
    @NotEmpty(message = "- Provide host directory to mount")
    String pathOnHost;
    @NotNull(message = "- Specify whether mount is read-only or read-write")
    MountMode mode;
}
