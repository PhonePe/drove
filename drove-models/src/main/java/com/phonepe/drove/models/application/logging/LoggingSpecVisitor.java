package com.phonepe.drove.models.application.logging;

/**
 *
 */
public interface LoggingSpecVisitor<T> {
    T visit(LocalLoggingSpec local);

    T visit(RsyslogLoggingSpec rsyslog);
}
