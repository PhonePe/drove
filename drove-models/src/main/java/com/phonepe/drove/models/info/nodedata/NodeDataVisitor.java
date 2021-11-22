package com.phonepe.drove.models.info.nodedata;

/**
 *
 */
public interface NodeDataVisitor<T> {

    T visit(ControllerNodeData controllerData);

    T visit(final ExecutorNodeData executorData);

}
