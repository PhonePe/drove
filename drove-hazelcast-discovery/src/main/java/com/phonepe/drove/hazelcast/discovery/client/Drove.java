package com.phonepe.drove.hazelcast.discovery.client;

import com.phonepe.drove.hazelcast.discovery.exception.DroveException;
import com.phonepe.drove.models.api.ApiResponse;
import com.phonepe.drove.models.instance.InstanceInfo;
import feign.Headers;
import feign.Param;
import feign.RequestLine;
import java.util.List;

public interface Drove {

    @RequestLine("GET /apis/v1/internal/instances")
    @Headers(value = {"Content-Type: application/json", "App-Instance-Authorization: {token}"})
    ApiResponse<List<InstanceInfo>> getAppInstances(@Param("token") String token) throws DroveException;
}
