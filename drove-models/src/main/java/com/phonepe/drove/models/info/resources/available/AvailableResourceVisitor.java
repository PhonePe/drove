package com.phonepe.drove.models.info.resources.available;

/**
 *
 */
public interface AvailableResourceVisitor<T> {
    T visit(AvailableCPU cpu);

    T visit(AvailableMemory memory);
}
