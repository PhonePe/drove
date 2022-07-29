package com.phonepe.drove.models.api;

import com.phonepe.drove.models.application.ApplicationSpec;
import com.phonepe.drove.models.application.ApplicationState;
import com.phonepe.drove.models.instance.InstanceInfo;
import lombok.Value;

import java.util.Date;
import java.util.List;

/**
 *
 */
@Value
public class AppDetails {
    String id;
    ApplicationSpec spec;
    long requiredInstances;
    long healthyInstances;
    long totalCPUs;
    long totalMemory;
    List<InstanceInfo> instances;
    ApplicationState state;
    Date created;
    Date updated;
}
