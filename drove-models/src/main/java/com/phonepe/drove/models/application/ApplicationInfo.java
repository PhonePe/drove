package com.phonepe.drove.models.application;

import lombok.Value;

import java.util.Date;

/**
 *
 */
@Value
public class ApplicationInfo {
    AppId appId;
    ApplicationSpec spec;
    ApplicationState state;
    Date created;
    Date updated;
}
