package com.phonepe.drove.controller.ui.views;

import com.phonepe.drove.models.api.AppDetails;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;
import ru.vyarus.guicey.gsp.views.template.TemplateView;

import java.util.Collection;

/**
 *
 */
@Value
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class ApplicationsView extends TemplateView {
    Collection<AppDetails> apps;

    public ApplicationsView(
            Collection<AppDetails> apps) {
        super("fragments/applications.hbs");
        this.apps = apps;
    }
}
