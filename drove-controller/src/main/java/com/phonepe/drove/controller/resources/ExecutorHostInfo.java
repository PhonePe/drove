package com.phonepe.drove.controller.resources;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Value;

import java.util.*;

/**
 *
 */
@Data
@AllArgsConstructor
public class ExecutorHostInfo {
    public enum CoreState {
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
    public static final class MemInfo {
        long available = 0L;
        long used = 0l;
    }

    @Value
    public static class NumaNodeInfo {
        Map<Integer, CoreState> cores = new HashMap<>();
        MemInfo memory = new MemInfo();
    }
    String executorId;
    Map<Integer, NumaNodeInfo> nodes;
}
