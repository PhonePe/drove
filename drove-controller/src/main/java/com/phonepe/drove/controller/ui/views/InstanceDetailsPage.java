package com.phonepe.drove.controller.ui.views;

import com.phonepe.drove.models.instance.InstanceInfo;
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
public class InstanceDetailsPage extends TemplateView {
    String appId;
    String instanceId;
    InstanceInfo instanceInfo;
    boolean hasReadAccess;

    public InstanceDetailsPage(
            String appId,
            String instanceId,
            InstanceInfo instanceInfo, boolean hasReadAccess) {
        super("templates/instancedetails.hbs");
        this.appId = appId;
        this.instanceId = instanceId;
        this.instanceInfo = instanceInfo;
        this.hasReadAccess = hasReadAccess;
    }
}
