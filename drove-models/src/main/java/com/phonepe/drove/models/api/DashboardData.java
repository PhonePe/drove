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

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.NonNull;
import lombok.Value;

@Value
@Builder
@Schema(description = "Comprehensive dashboard data containing cluster-wide statistics and summaries")
public final class DashboardData {
    @Value
    @Builder
    @Schema(description = "Application statistics including state distribution and top applications")
    public static final class AppStats {
        @Schema(description = "Count of applications grouped by their current state")
        @NonNull Map<ApplicationState, Long> appCountByState;

        @Schema(description = "List of top applications by resource usage or other metrics")
        @NonNull List<AppSummary> topApps;

        @Schema(description = "Total number of healthy application instances across the cluster", example = "150")
        long totalHealthyInstances;
    }

    @Value
    @Builder
    @Schema(description = "Task statistics including state distribution and top tasks")
    public static final class TaskStats {
        @Schema(description = "Count of tasks grouped by their current state")
        @NonNull Map<TaskState, Long> taskCountByState;

        @Schema(description = "List of top tasks by importance or other metrics")
        @NonNull List<TaskInfo> topTasks;
    }

    @Value
    @Builder
    @Schema(description = "Local service statistics including state distribution and top services")
    public static final class ServiceStats {
        @Schema(description = "Count of services grouped by their current lifecycle state")
        @NonNull Map<LocalServiceState, Long> serviceCountByState;

        @Schema(description = "Count of services grouped by their activation state")
        @NonNull Map<ActivationState, Long> serviceCountByActivationState;

        @Schema(description = "List of top services by resource usage or other metrics")
        @NonNull List<LocalServiceSummary> topServices;

        @Schema(description = "Total number of healthy service instances across the cluster", example = "75")
        long totalHealthyInstances;
    }

    @Value
    @Builder
    @Schema(description = "Cluster resource utilization statistics")
    public static final class UtilizationStats {
        @Schema(description = "Average resource utilization across all executors", example = "65.5")
        double averageUtilization;

        @Schema(description = "Highest resource utilization among all executors", example = "92.3")
        double highestUtilization;

        @Schema(description = "Lowest resource utilization among all executors", example = "23.1")
        double lowestUtilization;

        @Schema(description = "Load balance score indicating how evenly resources are distributed (0-100, higher is better)", example = "85.0")
        double balanceScore;
    }

    @Value
    @Builder
    @Schema(description = "Executor node statistics including state distribution and utilization metrics")
    public static final class ExecutorStats {
        @Schema(description = "Count of executor nodes grouped by their current state")
        @NonNull Map<ExecutorState, Long> executorCountByState;

        @Schema(description = "Resource utilization statistics across all executors")
        @NonNull UtilizationStats utilization;
    }

    @Schema(description = "High-level cluster summary information")
    @NonNull ClusterSummary clusterSummary;

    @Schema(description = "Application-related statistics")
    @NonNull AppStats appStats;

    @Schema(description = "Task-related statistics")
    @NonNull TaskStats taskStats;

    @Schema(description = "Local service-related statistics")
    @NonNull ServiceStats serviceStats;

    @Schema(description = "Executor node-related statistics")
    @NonNull ExecutorStats executorStats;

    @Schema(description = "Timestamp when this dashboard data was generated")
    @NonNull Date generatedAt;
}
