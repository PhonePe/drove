package com.phonepe.drove.models.api;

import com.phonepe.drove.models.info.nodedata.NodeTransportType;
import lombok.Value;

import java.util.Set;

/**
 *
 */
@Value
public class ExecutorSummary {
    public enum ExecutorState {
        ACTIVE,
        BLACKLISTED,
        REMOVED
    }
    String executorId;
    String hostname;
    int port;
    NodeTransportType transportType;
    int freeCores;
    int usedCores;
    long freeMemory;
    long usedMemory;
    Set<String> tags;
    ExecutorState state;
}
