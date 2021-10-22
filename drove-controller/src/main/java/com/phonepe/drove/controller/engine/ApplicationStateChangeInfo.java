package com.phonepe.drove.controller.engine;

import com.phonepe.drove.models.application.ApplicationState;
import lombok.Value;

/**
 *
 */
@Value
public class ApplicationStateChangeInfo {
    String appId;
    ApplicationState state;
}
