package com.phonepe.drove.controller.utils;

import com.phonepe.drove.controller.statedb.ApplicationStateDB;
import com.phonepe.drove.models.application.ApplicationSpec;
import com.phonepe.drove.models.instance.InstanceInfo;
import com.phonepe.drove.models.instance.InstanceState;
import com.phonepe.drove.models.operation.ApplicationOperation;
import com.phonepe.drove.models.operation.ApplicationOperationVisitor;
import com.phonepe.drove.models.operation.ClusterOpSpec;
import com.phonepe.drove.models.operation.ops.*;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;

import java.time.Duration;
import java.util.Objects;

/**
 *
 */
@Slf4j
@UtilityClass
public class ControllerUtils {

    public static String appId(ApplicationSpec applicationSpec) {
        return applicationSpec.getName();
    }

    public static boolean ensureInstanceState(
            ApplicationStateDB applicationStateDB,
            ClusterOpSpec clusterOpSpec,
            String appId,
            String instanceId,
            InstanceState required) {
        val retryPolicy = new RetryPolicy<Boolean>()
                .withDelay(Duration.ofSeconds(3))
                .withMaxAttempts(50)
                .withMaxDuration(Duration.ofMillis(clusterOpSpec.getTimeout().toMilliseconds()))
                .handle(Exception.class)
                .handleResultIf(r -> !r);

        try {
            val status = Failsafe.with(retryPolicy)
                    .onComplete(e -> {
                        val failure = e.getFailure();
                        if (null != failure) {
                            log.error("Error starting instance: {}", failure.getMessage());
                        }
                    })
                    .get(() -> ensureInstanceState(currentInstanceInfo(applicationStateDB, appId, instanceId),
                                                   required));
            if (status) {
                return true;
            }
            else {
                val curr = currentInstanceInfo(applicationStateDB, appId, instanceId);
                if (null == curr) {
                    log.error("No instance info found at all for: {}/{}", appId, instanceId);
                }
                else {
                    log.error("Looks like {}/{} is stuck in state: {}. Detailed instance data: {}}",
                              appId, instanceId, curr.getState(), curr);
                }
            }
        }
        catch (Exception e) {
            log.error("Error starting instance: " + appId + "/" + instanceId, e);
        }
        return false;
    }

    public static <O extends ApplicationOperation> O safeCast(
            final ApplicationOperation applicationOperation,
            Class<O> clazz) {
        val obj = applicationOperation.getClass().equals(clazz)
                  ? clazz.cast(applicationOperation)
                  : null;
        Objects.requireNonNull(obj,
                               "Cannot cast op " + applicationOperation.getClass()
                                       .getSimpleName() + " to " + clazz.getSimpleName());
        return obj;
    }

    private static InstanceInfo currentInstanceInfo(
            final ApplicationStateDB applicationStateDB,
            String appId,
            String instanceId) {
        return applicationStateDB.instance(appId, instanceId).orElse(null);
    }

    private static boolean ensureInstanceState(final InstanceInfo instanceInfo, final InstanceState instanceState) {
        if (null == instanceInfo) {
            return false;
        }
        log.debug("Intsance state for {}/{}: {}",
                  instanceInfo.getAppId(),
                  instanceInfo.getInstanceId(),
                  instanceInfo.getState());
        return instanceInfo.getState() == instanceState;
    }

    public static String appId(final ApplicationOperation operation) {
        return operation.accept(new ApplicationOperationVisitor<>() {
            @Override
            public String visit(ApplicationCreateOperation create) {
                return appId(create.getSpec());
            }

            @Override
            public String visit(ApplicationUpdateOperation update) {
                return update.getAppId();
            }

            @Override
            public String visit(ApplicationInfoOperation info) {
                return info.getAppId();
            }

            @Override
            public String visit(ApplicationDestroyOperation destroy) {
                return destroy.getAppId();
            }

            @Override
            public String visit(ApplicationDeployOperation deploy) {
                return deploy.getAppId();
            }

            @Override
            public String visit(ApplicationStopInstancesOperation stopInstances) {
                return stopInstances.getAppId();
            }

            @Override
            public String visit(ApplicationScaleOperation scale) {
                return scale.getAppId();
            }

            @Override
            public String visit(ApplicationRestartOperation restart) {
                return restart.getAppId();
            }

            @Override
            public String visit(ApplicationSuspendOperation suspend) {
                return suspend.getAppId();
            }

        });
    }
}
