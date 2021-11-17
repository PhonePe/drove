package com.phonepe.drove.models.api;

import lombok.Value;

/**
 *
 */
@Value
public class ClusterSummary {
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
