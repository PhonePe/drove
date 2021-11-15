package com.phonepe.drove.models.api;

import com.phonepe.drove.models.application.ApplicationSpec;
import com.phonepe.drove.models.application.ApplicationState;
import lombok.Value;

import java.util.Date;

/**
 *
 */
@Value
public class AppDetails {
    ApplicationSpec spec;
    long instances;
    ApplicationState state;
    Date created;
    Date updated;
}
