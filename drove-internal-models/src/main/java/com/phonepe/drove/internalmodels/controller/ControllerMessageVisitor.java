package com.phonepe.drove.internalmodels.controller;

/**
 *
 */
public interface ControllerMessageVisitor<T> {

    T visit(InstanceStateReportMessage instanceStateReport);

    T visit(ExecutorStateReportMessage executorStateReport);
}
