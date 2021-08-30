package com.phonepe.drove.common.messages.controller;

/**
 *
 */
public interface ControllerMessageVisitor<T> {

    T visit(InstanceStateReportMessage instanceStateReport);

    T visit(ExecutorStateReportMessage executorStateReport);
}
