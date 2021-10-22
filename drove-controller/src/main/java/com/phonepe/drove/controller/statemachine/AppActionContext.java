package com.phonepe.drove.controller.statemachine;

import com.phonepe.drove.common.ActionContext;
import com.phonepe.drove.controller.resources.ClusterResourcesDB;
import com.phonepe.drove.controller.statedb.ApplicationStateDB;
import com.phonepe.drove.models.application.ApplicationSpec;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 *
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class AppActionContext extends ActionContext {
    private final ApplicationSpec applicationSpec;
    private final ApplicationStateDB applicationStateDB;
    private final ClusterResourcesDB clusterResourcesDB;
}
