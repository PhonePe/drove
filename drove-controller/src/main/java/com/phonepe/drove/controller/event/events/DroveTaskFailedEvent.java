package com.phonepe.drove.controller.event.events;

import com.phonepe.drove.controller.event.DroveEvent;
import com.phonepe.drove.controller.event.DroveEventType;
import com.phonepe.drove.models.taskinstance.TaskState;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;

/**
 *
 */
@Value
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class DroveTaskFailedEvent extends DroveEvent {
    String sourceAppName;
    String taskId;
    TaskState state;
    String error;

    public DroveTaskFailedEvent(
            String sourceAppName, String taskId, TaskState state, String error) {
        super(DroveEventType.INSTANCE_FAILED);
        this.sourceAppName = sourceAppName;
        this.taskId = taskId;
        this.state = state;
        this.error = error;
    }
}
