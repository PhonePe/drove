package com.phonepe.drove.common.discovery;

import lombok.Data;

import javax.validation.constraints.NotEmpty;

/**
 *
 */
@Data
public class ZkConfig {
    @NotEmpty
    private String connectionString;

    private String nameSpace;

    private String hostname;

    private int port;
}
