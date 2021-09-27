package com.phonepe.drove.common.model.resources.available;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.phonepe.drove.models.application.requirements.ResourceType;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;

import java.util.Map;
import java.util.Set;

/**
 *
 */
@Value
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class AvailableCPU extends AvailableResource {
    /**
     * Storage model:
     * numa node id -> free cpu ids on that node
     */
    Map<Integer, Set<Integer>> cores;

    public AvailableCPU(@JsonProperty("cores") Map<Integer, Set<Integer>> cores) {
        super(ResourceType.CPU);
        this.cores = cores;
    }

    @Override
    public <T> T accept(AvailableResourceVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
