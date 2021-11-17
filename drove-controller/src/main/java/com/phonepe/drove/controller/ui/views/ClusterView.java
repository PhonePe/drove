package com.phonepe.drove.controller.ui.views;

import com.phonepe.drove.models.api.ClusterSummary;
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
public class ClusterView extends TemplateView {

    ClusterSummary summary;

    public ClusterView(ClusterSummary summary) {
        super("fragments/clustersummary.hbs");
        this.summary = summary;
    }
}
