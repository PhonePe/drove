package com.phonepe.drove.common.discovery.nodedata;

/**
 *
 */
public interface NodeDataVisitor<T> {

    T visit(ControllerNodeData controllerData);

    T visit(final ExecutorNodeData executorData);

}
