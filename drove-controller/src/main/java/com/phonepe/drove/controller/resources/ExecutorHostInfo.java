package com.phonepe.drove.controller.resources;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Value;

import java.util.*;

/**
 *
 */
@Value
public class ExecutorHostInfo {
    private enum CoreState {
        UNKNOWN,
        UNAVAILABLE,
        FREE,
        ALLOCATED,
        IN_USE
    }

    @Data
    @AllArgsConstructor
    public static final class CPUCoreInfo {
        private final int coreId;
        private CoreState state;
    }

    @Data
    @AllArgsConstructor
    public static final class MemInfo {
        long available;
        long used;
    }

    @Value
    public static class NumaNodeInfo {
        Set<CPUCoreInfo> cores = new TreeSet<>(Comparator.comparing(CPUCoreInfo::getCoreId));
        MemInfo memory;
    }
    String executorId;
    Map<Integer, NumaNodeInfo> nodes = new HashMap<>();
}
