package com.phonepe.drove.models.api;

import lombok.Value;

/**
 *
 */
@Value
public class ExecutorSummary {
    String executorId;
    String hostname;
    int port;
    int freeCores;
    int usedCores;
    long freeMemory;
    long usedMemory;
}
