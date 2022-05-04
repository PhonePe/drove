package com.phonepe.drove.executor.dockerauth;

import lombok.Data;

import javax.validation.constraints.NotEmpty;
import java.util.HashMap;
import java.util.Map;

/**
 *
 */
@Data
public class DockerAuthConfig {
    @NotEmpty
    private Map<String, DockerAuthConfigEntry> entries = new HashMap<>();
}
