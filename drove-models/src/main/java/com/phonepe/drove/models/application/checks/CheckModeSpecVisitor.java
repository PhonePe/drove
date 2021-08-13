package com.phonepe.drove.models.application.checks;

/**
 *
 */
public interface CheckModeSpecVisitor<T> {
    T visit(HTTPCheckModeSpec httpCheck);

    T visit(CmdCheckModeSpec cmdCheck);
}
