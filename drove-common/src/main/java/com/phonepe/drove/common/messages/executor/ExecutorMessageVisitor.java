package com.phonepe.drove.common.messages.executor;

/**
 *
 */
public interface ExecutorMessageVisitor<T> {
    T visit(StartInstanceMessage startInstanceMessage);

    T visit(StopInstanceMessage stopInstanceMessage);

    T visit(QueryInstanceMessage queryInstanceMessage);
}
