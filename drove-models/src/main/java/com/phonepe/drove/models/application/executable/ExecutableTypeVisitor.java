package com.phonepe.drove.models.application.executable;

/**
 *
 */
public interface ExecutableTypeVisitor<T> {
    T visit(DockerCoordinates dockerCoordinates);
}
