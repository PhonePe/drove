package com.phonepe.drove.models.application;

import lombok.Value;

import java.util.Date;

/**
 *
 */
@Value
public class ApplicationInfo {
    String appId;
    ApplicationSpec spec;
    long instances;
    Date created;
    Date updated;
}
