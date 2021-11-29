package com.phonepe.drove.controller.utils;

import com.phonepe.drove.controller.resourcemgmt.ExecutorHostInfo;
import com.phonepe.drove.controller.statedb.ApplicationStateDB;
import com.phonepe.drove.models.application.ApplicationSpec;
import com.phonepe.drove.models.info.nodedata.ControllerNodeData;
import com.phonepe.drove.models.info.nodedata.ExecutorNodeData;
import com.phonepe.drove.models.info.nodedata.NodeDataVisitor;
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
import java.util.Set;

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
        log.trace("Instance state for {}/{}: {}",
                  instanceInfo.getAppId(), instanceInfo.getInstanceId(), instanceInfo.getState());
        if(instanceInfo.getState() == instanceState) {
            log.info("Instance {}/{} reached desired state: {}", instanceInfo.getAppId(), instanceInfo.getInstanceId(), instanceState);
            return true;
        }
        return false;
    }

    public static String appId(final ApplicationOperation operation) {
        return operation.accept(new ApplicationOperationVisitor<>() {
            @Override
            public String visit(ApplicationCreateOperation create) {
                return appId(create.getSpec());
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
            public String visit(ApplicationReplaceInstancesOperation replaceInstances) {
                return replaceInstances.getAppId();
            }

            @Override
            public String visit(ApplicationSuspendOperation suspend) {
                return suspend.getAppId();
            }

            @Override
            public String visit(ApplicationRecoverOperation recover) {
                return recover.getAppId();
            }

        });
    }

    public static long usedMemory(ExecutorHostInfo executor) {
        return executor.getNodeData()
                .accept(new NodeDataVisitor<Long>() {
                    @Override
                    public Long visit(ControllerNodeData controllerData) {
                        return 0L;
                    }

                    @Override
                    public Long visit(ExecutorNodeData executorData) {
                        return executorData.getState().getMemory().getUsedMemory().values()
                                .stream()
                                .mapToLong(v -> v)
                                .sum();
                    }
                });
    }

    public static long freeMemory(ExecutorHostInfo executor) {
        return executor.getNodeData()
                .accept(new NodeDataVisitor<Long>() {
                    @Override
                    public Long visit(ControllerNodeData controllerData) {
                        return 0L;
                    }

                    @Override
                    public Long visit(ExecutorNodeData executorData) {
                        return executorData.getState().getMemory().getFreeMemory().values()
                                .stream()
                                .mapToLong(v -> v)
                                .sum();
                    }
                });
    }

    public static int freeCores(ExecutorHostInfo executor) {
        return executor.getNodeData()
                .accept(new NodeDataVisitor<Integer>() {
                    @Override
                    public Integer visit(ControllerNodeData controllerData) {
                        return 0;
                    }

                    @Override
                    public Integer visit(ExecutorNodeData executorData) {
                        return executorData.getState().getCpus().getFreeCores().values()
                                .stream()
                                .mapToInt(Set::size)
                                .sum();
                    }
                });
    }

    public static int usedCores(ExecutorHostInfo executor) {
        return executor.getNodeData()
                .accept(new NodeDataVisitor<Integer>() {
                    @Override
                    public Integer visit(ControllerNodeData controllerData) {
                        return 0;
                    }

                    @Override
                    public Integer visit(ExecutorNodeData executorData) {
                        return executorData.getState().getCpus().getUsedCores().values()
                                .stream()
                                .mapToInt(Set::size)
                                .sum();
                    }
                });
    }
}
