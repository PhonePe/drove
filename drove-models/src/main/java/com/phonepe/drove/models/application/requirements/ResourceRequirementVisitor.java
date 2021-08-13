package com.phonepe.drove.models.application.requirements;

/**
 *
 */
public interface ResourceRequirementVisitor<T> {
    T visit(CPURequirement cpuRequirement);

    T visit(MemoryRequirement memoryRequirement);
}
