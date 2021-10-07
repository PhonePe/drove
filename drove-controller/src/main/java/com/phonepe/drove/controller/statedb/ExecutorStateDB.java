package com.phonepe.drove.controller.statedb;

import com.phonepe.drove.common.model.ExecutorResourceSnapshot;

import java.util.List;

/**
 *
 */
public interface ExecutorStateDB {
    List<ExecutorResourceSnapshot> executorState(int start, int size);
    boolean updateExecutorState(String executorId, final ExecutorResourceSnapshot executorState);
    boolean deleteExecutorState(String executorId);

}
