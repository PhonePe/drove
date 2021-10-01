package com.phonepe.drove.controller.statedb;

import com.phonepe.drove.common.model.ExecutorState;

import java.util.List;

/**
 *
 */
public interface ExecutorStateDB {
    List<ExecutorState> executorState(int start, int size);
    boolean updateExecutorState(String executorId, final ExecutorState executorState);
    boolean deleteExecutorState(String executorId);

}
