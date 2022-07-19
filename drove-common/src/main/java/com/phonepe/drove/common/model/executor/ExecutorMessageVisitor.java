package com.phonepe.drove.common.model.executor;

/**
 *
 */
public interface ExecutorMessageVisitor<T> {
    T visit(StartInstanceMessage startInstanceMessage);

    T visit(StopInstanceMessage stopInstanceMessage);

    T visit(StartTaskInstanceMessage startTaskInstanceMessage);

    T visit(StopTaskInstanceMessage stopTaskInstanceMessage);

    T visit(BlacklistExecutorMessage blacklistExecutorMessage);

    T visit(UnBlacklistExecutorMessage unBlacklistExecutorMessage);

}
