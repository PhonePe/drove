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
    Date created;
    Date updated;
}
