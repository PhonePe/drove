package com.phonepe.drove.models.api;

import com.phonepe.drove.models.common.ClusterState;
import lombok.Value;

/**
 *
 */
@Value
public class ClusterSummary {
    String leader;
    ClusterState state;
    int numExecutors;
    int numApplications;
    int numActiveApplications;
    int freeCores;
    int usedCores;
    int totalCores;
    long freeMemory;
    long usedMemory;
    long totalMemory;
}
