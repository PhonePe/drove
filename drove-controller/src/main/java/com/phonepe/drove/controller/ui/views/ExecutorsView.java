package com.phonepe.drove.controller.ui.views;

import com.phonepe.drove.models.api.ExecutorSummary;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;
import ru.vyarus.guicey.gsp.views.template.TemplateView;

import java.util.List;

/**
 *
 */
@Value
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class ExecutorsView extends TemplateView {
    List<ExecutorSummary> executors;

    public ExecutorsView(List<ExecutorSummary> executors) {
        super("fragments/executors.hbs");
        this.executors = executors;
    }
}
