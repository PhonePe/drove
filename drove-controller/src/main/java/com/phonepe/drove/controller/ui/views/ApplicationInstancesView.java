package com.phonepe.drove.controller.ui.views;

import com.phonepe.drove.models.instance.InstanceInfo;
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
public class ApplicationInstancesView extends TemplateView {
    Collection<InstanceInfo> instances;

    public ApplicationInstancesView(Collection<InstanceInfo> instances) {
        super("fragments/applicationinstances.hbs");
        this.instances = instances;
    }
}
