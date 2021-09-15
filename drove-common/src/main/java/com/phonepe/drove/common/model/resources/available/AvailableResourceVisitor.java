package com.phonepe.drove.common.model.resources.available;

/**
 *
 */
public interface AvailableResourceVisitor<T> {
    T visit(AvailableCPU cpu);

    T visit(AvailableMemory memory);
}
