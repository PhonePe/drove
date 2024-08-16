/*
 *  Copyright (c) 2024 Original Author(s), PhonePe India Pvt. Ltd.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.phonepe.drove.auth.clientfilter;

import com.phonepe.drove.auth.core.AuthConstants;
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
                .putAll(Map.of(AuthConstants.NODE_ID_HEADER, List.of(nodeId),
                               ClusterCommHeaders.CLUSTER_AUTHORIZATION, List.of(secret)));
    }
}
