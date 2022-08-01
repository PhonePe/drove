package com.phonepe.drove.common.zookeeper;

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

    private int port;
}