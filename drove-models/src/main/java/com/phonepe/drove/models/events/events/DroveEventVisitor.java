package com.phonepe.drove.models.events.events;

/**
 * To handle event types in a type-safe manner
 */
public interface DroveEventVisitor<T> {
    T visit(DroveAppStateChangeEvent appStateChanged);

    T visit(DroveInstanceStateChangeEvent instanceStateChanged);

    T visit(DroveTaskStateChangeEvent taskStateChanged);

    T visit(DroveExecutorAddedEvent executorAdded);

    T visit(DroveExecutorRemovedEvent executorRemoved);

    T visit(DroveExecutorBlacklistedEvent executorBlacklisted);

    T visit(DroveExecutorUnblacklistedEvent executorUnBlacklisted);

    T visit(DroveClusterMaintenanceModeSetEvent clusterMaintenanceModeSet);

    T visit(DroveClusterMaintenanceModeRemovedEvent clusterMaintenanceModeRemoved);

    T visit(DroveClusterLeadershipAcquiredEvent leadershipAcquired);

    T visit(DroveClusterLeadershipLostEvent leadershipLost);


}
