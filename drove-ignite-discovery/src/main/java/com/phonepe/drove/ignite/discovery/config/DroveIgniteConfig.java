package com.phonepe.drove.ignite.discovery.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Duration;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class DroveIgniteConfig {

    private String droveEndpoint;

    private String transportName;

    private String communicationPortName;

    private String discoveryPortName;

    private boolean useAppNameForDiscovery;

    private Duration leaderElectionMaxRetryDuration;
}
