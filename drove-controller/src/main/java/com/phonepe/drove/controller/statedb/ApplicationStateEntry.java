package com.phonepe.drove.controller.statedb;

import com.phonepe.drove.models.application.ApplicationInfo;
import com.phonepe.drove.models.instance.InstanceInfo;
import lombok.Value;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 *
 */
@Value
public class ApplicationStateEntry {
    ApplicationInfo applicationInfo;
    Map<String, InstanceInfo> instances;
}
