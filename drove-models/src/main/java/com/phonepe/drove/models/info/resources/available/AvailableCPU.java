package com.phonepe.drove.models.info.resources.available;

import com.phonepe.drove.models.application.requirements.ResourceType;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.util.Map;
import java.util.Set;

/**
 *
 */
@Value
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Jacksonized
@Builder
public class AvailableCPU extends AvailableResource {
    /**
     * Storage model:
     * numa node id -> free cpu ids on that node
     */
    Map<Integer, Set<Integer>> freeCores;
    Map<Integer, Set<Integer>> usedCores;

    public AvailableCPU(
            Map<Integer, Set<Integer>> freeCores,
            Map<Integer, Set<Integer>> usedCores) {
        super(ResourceType.CPU);
        this.freeCores = freeCores;
        this.usedCores = usedCores;
    }

    @Override
    public <T> T accept(AvailableResourceVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
