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

package com.phonepe.drove.executor.discovery;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.phonepe.drove.executor.managed.ExecutorIdManager;
import com.phonepe.drove.models.api.ApiResponse;
import com.phonepe.drove.models.info.nodedata.NodeTransportType;
import com.phonepe.drove.models.internal.KnownInstancesData;
import com.phonepe.drove.models.internal.LocalServiceInstanceResources;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.apache.hc.core5.http.io.entity.EntityUtils;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

/**
 *
 */
@Singleton
@Slf4j
public class ClusterClient {
    private final ExecutorIdManager executorIdManager;
    private final ManagedLeadershipObserver leadershipObserver;
    private final ObjectMapper mapper;

    private final CloseableHttpClient httpClient;

    @Inject
    public ClusterClient(
            ExecutorIdManager executorIdManager,
            ManagedLeadershipObserver leadershipObserver,
            ObjectMapper mapper,
            @Named("ControllerHttpClient") CloseableHttpClient httpClient) {
        this.executorIdManager = executorIdManager;
        this.leadershipObserver = leadershipObserver;
        this.mapper = mapper;
        this.httpClient = httpClient;
    }

    public KnownInstancesData lastKnownInstances() {
        return readSnapshot("last");
    }

    public KnownInstancesData currentKnownInstances() {
        return readSnapshot("current");
    }

    public LocalServiceInstanceResources reservedResources() throws ControllerCommunicationError {
        val leader = leadershipObserver.leader().orElse(null);
        if (null == leader) {
            throw ControllerCommunicationError.noLeader();
        }
        try {
            val uri = String.format("%s://%s:%d/apis/v1/internal/cluster/resources/reserved",
                                    leader.getTransportType() == NodeTransportType.HTTP
                                    ? "http"
                                    : "https",
                                    leader.getHostname(),
                                    leader.getPort());
            val request = new HttpGet(uri);
            val response = httpClient.execute(
                    request,
                    (HttpClientResponseHandler<ApiResponse<LocalServiceInstanceResources>>) callResponse -> {
                        if (callResponse.getCode() == 200) {
                            return mapper.readValue(EntityUtils.toByteArray(callResponse.getEntity()),
                                                    new TypeReference<>() {
                                                    });
                        }
                        throw ControllerCommunicationError.commError(
                                callResponse.getCode(), EntityUtils.toString(callResponse.getEntity()));
                    });
            if (null == response.getData()) {
                return LocalServiceInstanceResources.EMPTY;
            }
            return response.getData();
        }
        catch (ControllerCommunicationError e) {
            throw e;
        }
        catch (Exception e) {
            throw ControllerCommunicationError.commError(e);
        }
    }

    private KnownInstancesData readSnapshot(String from) throws ControllerCommunicationError {
        val executorId = executorIdManager.executorId().orElse(null);
        if (null == executorId) {
            log.info("Executor Id not yet available. Cannot fetch last state data from controller.");
            return KnownInstancesData.EMPTY;
        }
        val leader = leadershipObserver.leader().orElse(null);
        if (null == leader) {
            log.info("Leader not found for cluster. Cannot fetch last state data from controller.");
            return KnownInstancesData.EMPTY;
        }
        try {
            val uri =
                    String.format("%s://%s:%d/apis/v1/internal/cluster/executors/" + executorId + "/instances/" + from,
                                  leader.getTransportType() == NodeTransportType.HTTP
                                  ? "http"
                                  : "https",
                                  leader.getHostname(),
                                  leader.getPort());
            val request = new HttpGet(uri);
            val response = httpClient.execute(request,
                                              (HttpClientResponseHandler<ApiResponse<KnownInstancesData>>) callResponse -> {
                                                  if (callResponse.getCode() == 200) {
                                                      return mapper.readValue(EntityUtils.toByteArray(callResponse.getEntity()),
                                                                              new TypeReference<>() {
                                                                              });
                                                  }
                                                  log.error(
                                                          "Error fetching last known status from leader. Received" +
                                                                  " response: [{}] {}",
                                                          callResponse.getCode(),
                                                          EntityUtils.toString(callResponse.getEntity()));
                                                  return null;
                                              });
            if (null == response || null == response.getData()) {
                return KnownInstancesData.EMPTY;
            }
            return response.getData();

        }
        catch (ControllerCommunicationError e) {
            throw e;
        }
        catch (Exception e) {
            log.error("Could not fetch last known instances data. Error " + e.getMessage(), e);
        }
        return KnownInstancesData.EMPTY;
    }
}
