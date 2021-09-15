package com.phonepe.drove.controller.statemachine;

import com.phonepe.drove.common.Action;
import com.phonepe.drove.models.application.ApplicationInfo;
import com.phonepe.drove.models.application.ApplicationState;

/**
 *
 */
public abstract class AppAction implements Action<ApplicationInfo, ApplicationState, AppActionContext> {
}
