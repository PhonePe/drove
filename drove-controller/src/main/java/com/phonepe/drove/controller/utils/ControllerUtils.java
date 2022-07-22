package com.phonepe.drove.controller.utils;

import com.phonepe.drove.common.CommonUtils;
import com.phonepe.drove.controller.engine.ControllerRetrySpecFactory;
import com.phonepe.drove.controller.resourcemgmt.ExecutorHostInfo;
import com.phonepe.drove.controller.statedb.ApplicationInstanceInfoDB;
import com.phonepe.drove.models.api.ApiResponse;
import com.phonepe.drove.models.application.ApplicationSpec;
import com.phonepe.drove.models.info.nodedata.ControllerNodeData;
import com.phonepe.drove.models.info.nodedata.ExecutorNodeData;
import com.phonepe.drove.models.info.nodedata.NodeDataVisitor;
import com.phonepe.drove.models.instance.InstanceInfo;
import com.phonepe.drove.models.instance.InstanceState;
import com.phonepe.drove.models.interfaces.DeploymentSpec;
import com.phonepe.drove.models.interfaces.DeploymentSpecVisitor;
import com.phonepe.drove.models.operation.*;
import com.phonepe.drove.models.operation.ops.*;
import com.phonepe.drove.models.operation.taskops.TaskCreateOperation;
import com.phonepe.drove.models.operation.taskops.TaskKillOperation;
import com.phonepe.drove.models.task.TaskSpec;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import net.jodah.failsafe.Failsafe;

import javax.ws.rs.core.Response;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static com.phonepe.drove.controller.utils.StateCheckStatus.*;

/**
 *
 */
@Slf4j
@UtilityClass
public class ControllerUtils {

    private static final Set<StateCheckStatus> CHECK_COMPLETED_STATES = EnumSet.of(MISNMATCH_NONRECOVERABLE, MATCH);
    public static String deployableObjectId(DeploymentSpec deploymentSpec) {
        return deploymentSpec.accept(new DeploymentSpecVisitor<String>() {
            @Override
            public String visit(ApplicationSpec applicationSpec) {
                return applicationSpec.getName() + "-" + applicationSpec.getVersion();
            }

            @Override
            public String visit(TaskSpec taskSpec) {
                return taskSpec.getSourceApp() + "-" + taskSpec.getTaskId();
            }
        });
    }

    public static boolean ensureInstanceState(
            final ApplicationInstanceInfoDB instanceInfoDB,
            ClusterOpSpec clusterOpSpec,
            String appId,
            String instanceId,
            InstanceState required,
            ControllerRetrySpecFactory retrySpecFactory) {
        val retryPolicy =
                CommonUtils.<StateCheckStatus>policy(
                        retrySpecFactory.instanceStateCheckRetrySpec(clusterOpSpec.getTimeout().toMilliseconds()),
                        MISMATCH::equals);
        try {
            val status = Failsafe.with(List.of(retryPolicy))
                    .onComplete(e -> {
                        val failure = e.getFailure();
                        if (null != failure) {
                            log.error("Error starting instance: {}", failure.getMessage());
                        }
                    })
                    .get(() -> ensureInstanceState(currentInstanceInfo(instanceInfoDB, appId, instanceId),
                                                   required));
            if (status.equals(MATCH)) {
                return true;
            }
            else {
                val curr = currentInstanceInfo(instanceInfoDB, appId, instanceId);
                if (null == curr) {
                    log.error("No instance info found at all for: {}/{}", appId, instanceId);
                }
                else {
                    if(status.equals(MISMATCH)) {
                        log.error("Looks like {}/{} is stuck in state: {}. Detailed instance data: {}}",
                                  appId, instanceId, curr.getState(), curr);
                    }
                    else {
                        log.error("Looks like {}/{} has failed permanently and reached state: {}. Detailed instance data: {}}",
                                  appId, instanceId, curr.getState(), curr);
                    }
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
            final ApplicationInstanceInfoDB instanceInfoDB,
            String appId,
            String instanceId) {
        return instanceInfoDB.instance(appId, instanceId).orElse(null);
    }

    private static StateCheckStatus ensureInstanceState(final InstanceInfo instanceInfo, final InstanceState instanceState) {
        if (null != instanceInfo) {
            val currState = instanceInfo.getState();
            log.trace("Instance state for {}/{}: {}",
                      instanceInfo.getAppId(), instanceInfo.getInstanceId(), currState);
            if (currState == instanceState) {
                log.info("Instance {}/{} reached desired state: {}",
                         instanceInfo.getAppId(),
                         instanceInfo.getInstanceId(),
                         instanceState);
                return MATCH;
            }
            if(currState.isTerminal()) { //Useless to wait if it has died anyways
                return MISNMATCH_NONRECOVERABLE;
            }
        }
        return MISMATCH;
    }

    public static String deployableObjectId(final ApplicationOperation operation) {
        return operation.accept(new ApplicationOperationVisitor<>() {
            @Override
            public String visit(ApplicationCreateOperation create) {
                return deployableObjectId(create.getSpec());
            }

            @Override
            public String visit(ApplicationDestroyOperation destroy) {
                return destroy.getAppId();
            }

            @Override
            public String visit(ApplicationStartInstancesOperation deploy) {
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
    
    public static String deployableObjectId(final TaskOperation operation) {
        return operation.accept(new TaskOperationVisitor<String>() {
            @Override
            public String visit(TaskCreateOperation create) {
                return deployableObjectId(create.getSpec());
            }

            @Override
            public String visit(TaskKillOperation kill) {
                return kill.getTaskId();
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

    public static <T> Response ok(final T data) {
        return Response.ok(ApiResponse.success(data)).build();
    }

    public static <T> Response badRequest(T data, String message) {
        return Response.status(Response.Status.BAD_REQUEST)
                .entity(ApiResponse.failure(data, message))
                .build();
    }
}
