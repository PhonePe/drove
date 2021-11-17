package com.phonepe.drove.controller.ui.views;

import com.phonepe.drove.models.api.AppSummary;
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
public class ApplicationSummaryView extends TemplateView {
    AppSummary app;

    public ApplicationSummaryView(AppSummary app) {
        super("fragments/applicationsummary.hbs");
        this.app = app;
    }
}
