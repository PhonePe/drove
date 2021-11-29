package com.phonepe.drove.common.model.executor;

/**
 *
 */
public interface ExecutorMessageVisitor<T> {
    T visit(StartInstanceMessage startInstanceMessage);

    T visit(StopInstanceMessage stopInstanceMessage);

    T visit(QueryInstanceMessage queryInstanceMessage);

    T visit(BlacklistExecutorMessage blacklistExecutorMessage);

    T visit(UnBlacklistExecutorMessage unBlacklistExecutorMessage);
}
