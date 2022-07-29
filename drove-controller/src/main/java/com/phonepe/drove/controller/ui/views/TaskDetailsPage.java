package com.phonepe.drove.controller.ui.views;

import com.phonepe.drove.models.taskinstance.TaskInfo;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;
import ru.vyarus.guicey.gsp.views.template.TemplateView;

/**
 *
 */
@Value
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class TaskDetailsPage extends TemplateView {
    String sourceAppName;
    String taskId;
    TaskInfo instanceInfo;

    public TaskDetailsPage(
            String sourceAppName,
            String taskId,
            TaskInfo instanceInfo) {
        super("templates/taskdetails.hbs");
        this.sourceAppName = sourceAppName;
        this.taskId = taskId;
        this.instanceInfo = instanceInfo;
    }
}
