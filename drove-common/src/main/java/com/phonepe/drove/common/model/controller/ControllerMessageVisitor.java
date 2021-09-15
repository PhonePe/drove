package com.phonepe.drove.common.model.controller;

/**
 *
 */
public interface ControllerMessageVisitor<T> {

    T visit(InstanceStateReportMessage instanceStateReport);

    T visit(ExecutorStateReportMessage executorStateReport);
}
