package com.phonepe.drove.common.model.resources.allocation;

/**
 *
 */
public interface ResourceAllocationVisitor<T> {
    T visit(final CPUAllocation cpu);

    T visit(final MemoryAllocation memory);
}
