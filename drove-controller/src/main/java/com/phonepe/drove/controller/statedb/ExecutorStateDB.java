package com.phonepe.drove.controller.statedb;

import com.phonepe.drove.common.model.ExecutorResourceSnapshot;

import java.util.List;
import java.util.Optional;

/**
 *
 */
public interface ExecutorStateDB {
    Optional<ExecutorResourceSnapshot> executorState(final String executorId);
    List<ExecutorResourceSnapshot> executorState(int start, int size);
    boolean updateExecutorState(String executorId, final ExecutorResourceSnapshot executorState);
    boolean deleteExecutorState(String executorId);

}
