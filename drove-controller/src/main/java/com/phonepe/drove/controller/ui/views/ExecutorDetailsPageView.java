package com.phonepe.drove.controller.ui.views;

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
public class ExecutorDetailsPageView extends TemplateView {

    String executorId;

    public ExecutorDetailsPageView(String executorId) {
        super("templates/executordetails.hbs");
        this.executorId = executorId;
    }
}
