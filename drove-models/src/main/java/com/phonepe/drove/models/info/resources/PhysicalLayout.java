package com.phonepe.drove.models.info.resources;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.util.Map;
import java.util.Set;

/**
 * Physical layout of the host
 */
@Value
@Jacksonized
@AllArgsConstructor
@Builder
public class PhysicalLayout {
    Map<Integer, Set<Integer>> cores;
    Map<Integer, Long> memory;
}
