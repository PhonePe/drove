package com.phonepe.drove.controller.statedb;

import com.phonepe.drove.common.model.ExecutorResourceSnapshot;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.phonepe.drove.common.CommonUtils.sublist;

/**
 *
 */
public class MapBasedExecutorStateDB implements ExecutorStateDB {
    Map<String, ExecutorResourceSnapshot> executors = new ConcurrentHashMap<>();

    @Override
    public List<ExecutorResourceSnapshot> executorState(int start, int size) {
        //TODO:: THIS IS NOT PERFORMANT IN TERMS OF MEMORY
        return sublist(List.copyOf(executors.values()), start, size);
    }

    @Override
    public boolean updateExecutorState(
            String executorId, ExecutorResourceSnapshot executorState) {
        executors.compute(executorId, (id, oldValue) -> executorState);
        return true;
    }

    @Override
    public boolean deleteExecutorState(String executorId) {
        executors.remove(executorId);
        return true;
    }
}
