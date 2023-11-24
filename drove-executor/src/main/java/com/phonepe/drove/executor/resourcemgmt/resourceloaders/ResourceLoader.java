package com.phonepe.drove.executor.resourcemgmt.resourceloaders;

import com.phonepe.drove.executor.resourcemgmt.ResourceManager;

import java.util.Map;

public interface ResourceLoader {
    Map<Integer, ResourceManager.NodeInfo> loadSystemResources();
}
