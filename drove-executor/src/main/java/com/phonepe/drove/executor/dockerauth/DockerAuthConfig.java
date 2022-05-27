package com.phonepe.drove.executor.dockerauth;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotEmpty;
import java.util.HashMap;
import java.util.Map;

/**
 *
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class DockerAuthConfig {
    public static final DockerAuthConfig DEFAULT = new DockerAuthConfig(Map.of());
    @NotEmpty
    private Map<String, DockerAuthConfigEntry> entries = new HashMap<>();
}
