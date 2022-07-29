package com.phonepe.drove.auth.clientfilter;

import com.phonepe.drove.auth.core.AuthConstansts;
import com.phonepe.drove.auth.model.ClusterCommHeaders;

import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientRequestFilter;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 *
 */
public class DroveClientRequestFilter implements ClientRequestFilter {
    private final String nodeId;
    private final String secret;

    public DroveClientRequestFilter(String nodeId, String secret) {
        this.nodeId = nodeId;
        this.secret = secret;
    }

    @Override
    public void filter(ClientRequestContext requestContext) throws IOException {
        requestContext.getHeaders()
                .putAll(Map.of(AuthConstansts.NODE_ID_HEADER, List.of(nodeId),
                               ClusterCommHeaders.CLUSTER_AUTHORIZATION, List.of(secret)));
    }
}
