package com.phonepe.drove.models.api;

import java.util.Date;
import java.util.List;
import java.util.Map;

import com.phonepe.drove.models.application.ApplicationState;
import com.phonepe.drove.models.info.nodedata.ExecutorState;
import com.phonepe.drove.models.localservice.ActivationState;
import com.phonepe.drove.models.localservice.LocalServiceState;
import com.phonepe.drove.models.taskinstance.TaskInfo;
import com.phonepe.drove.models.taskinstance.TaskState;

import lombok.Builder;
import lombok.NonNull;
import lombok.Value;

@Value
@Builder
public final class DashboardData {
    @Value
    @Builder
    public static final class AppStats {
        @NonNull Map<ApplicationState, Long> appCountByState;
        @NonNull List<AppSummary> topApps;
        long totalHealthyInstances;
    }

    @Value
    @Builder
    public static final class TaskStats {
        @NonNull Map<TaskState, Long> taskCountByState;
        @NonNull List<TaskInfo> topTasks;
    }

    @Value
    @Builder
    public static final class ServiceStats {
        @NonNull Map<LocalServiceState, Long> serviceCountByState;
        @NonNull Map<ActivationState, Long> serviceCountByActivationState;
        @NonNull List<LocalServiceSummary> topServices;
        long totalHealthyInstances;
    }

    @Value
    @Builder
    public static final class UtilizationStats {
        double averageUtilization;
        double highestUtilization;
        double lowestUtilization;
        double balanceScore;
    }

    @Value
    @Builder
    public static final class ExecutorStats {
        @NonNull Map<ExecutorState, Long> executorCountByState;
        @NonNull UtilizationStats utilization;
        //@NonNull List<ExecutorSummary> topExecutors;
    }

    @NonNull ClusterSummary clusterSummary;
    @NonNull AppStats appStats;
    @NonNull TaskStats taskStats;
    @NonNull ServiceStats serviceStats;
    @NonNull ExecutorStats executorStats;
    @NonNull Date generatedAt;
}
