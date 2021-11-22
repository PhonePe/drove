package com.phonepe.drove.models.info.resources.allocation;

/**
 *
 */
public interface ResourceAllocationVisitor<T> {
    T visit(final CPUAllocation cpu);

    T visit(final MemoryAllocation memory);
}
