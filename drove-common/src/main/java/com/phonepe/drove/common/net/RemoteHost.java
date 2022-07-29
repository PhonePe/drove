package com.phonepe.drove.common.net;

import com.phonepe.drove.models.info.nodedata.NodeTransportType;
import lombok.Value;

/**
 *
 */
@Value
public class RemoteHost {
    String hostname;
    int port;
    NodeTransportType transportType;
}
