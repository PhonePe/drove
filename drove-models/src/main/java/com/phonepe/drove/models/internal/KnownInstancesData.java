package com.phonepe.drove.models.internal;

import lombok.Value;

import java.util.Set;

/**
 *
 */
@Value
public class KnownInstancesData {
    public static final KnownInstancesData EMPTY = new KnownInstancesData(Set.of(), Set.of(), Set.of(), Set.of());

    Set<String> appInstanceIds;
    Set<String> staleAppInstanceIds;
    Set<String> taskInstanceIds;
    Set<String> staleTaskInstanceIds;

}
