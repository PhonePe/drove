package com.phonepe.drove.controller.event;

/**
 *
 */
public enum DroveEventType {
    APP_STATE_CHANGE,
    INSTANCE_STATE_CHANGE,
    TASK_STATE_CHANGE,
    EXECUTOR_ADDED,
    EXECUTOR_REMOVED,
    EXECUTOR_BLACKLISTED,
    EXECUTOR_UN_BLACKLISTED,
    MAINTENANCE_MODE_SET,
    MAINTENANCE_MODE_REMOVED,

    LEADERSHIP_ACQUIRED,
    LEADERSHIP_LOST
}
