package com.phonepe.drove.models.info.resources.available;

import com.phonepe.drove.models.application.requirements.ResourceType;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.util.List;
@Value
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Jacksonized
@Builder
public class AvailableGPU extends AvailableResource{

    List<String> freeGpus;
    List<String> usedGpus;

    public AvailableGPU(List<String> freeGpus, List<String> usedGpus) {
        super(ResourceType.GPU);
        this.usedGpus = usedGpus;
        this.freeGpus = freeGpus;
    }

    @Override
    public <T> T accept(AvailableResourceVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
