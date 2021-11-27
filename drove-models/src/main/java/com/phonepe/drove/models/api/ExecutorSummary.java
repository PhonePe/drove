package com.phonepe.drove.models.api;

import lombok.Value;

import java.util.Set;

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
    Set<String> tags;
}
