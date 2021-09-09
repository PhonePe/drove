package com.phonepe.drove.internalmodels.executor;

/**
 *
 */
public interface ExecutorMessageVisitor<T> {
    T visit(StartInstanceMessage startInstanceMessage);

    T visit(StopInstanceMessage stopInstanceMessage);

    T visit(QueryInstanceMessage queryInstanceMessage);
}
