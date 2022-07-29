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
public class LogPailerPage extends TemplateView {
    String logType;
    String appId;
    String instanceId;
    String logFileName;

    public LogPailerPage(String logType, String appId, String instanceId, String logFileName) {
        super("templates/logtailer.hbs");
        this.logType = logType;
        this.appId = appId;
        this.instanceId = instanceId;
        this.logFileName = logFileName;
    }
}
