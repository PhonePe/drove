package com.phonepe.drove.models.application.changenotification;

/**
 *
 */
public interface StateChangeNotificationSpecVisitor<T> {
    T visit(URLStateChangeNotificationSpec urlStateChangeNotificationSpec);
}
