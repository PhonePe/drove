package com.phonepe.drove.common.model.executor;

import com.phonepe.drove.models.info.nodedata.NodeTransportType;
import lombok.Value;

/**
 *
 */
@Value
public class ExecutorAddress {
    String executorId;
    String hostname;
    int port;
    NodeTransportType transportType;
}
