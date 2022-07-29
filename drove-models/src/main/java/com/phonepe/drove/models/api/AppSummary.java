package com.phonepe.drove.models.api;

import com.phonepe.drove.models.application.ApplicationState;
import lombok.Value;

import java.util.Date;

/**
 *
 */
@Value
public class AppSummary {
    String id;
    String name;
    long requiredInstances;
    long healthyInstances;
    long totalCPUs;
    long totalMemory;
    ApplicationState state;
    Date created;
    Date updated;
}
