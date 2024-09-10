package com.phonepe.drove.ignite.discovery;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Strings;
import com.phonepe.drove.client.DroveClient;
import com.phonepe.drove.models.api.ApiErrorCode;
import com.phonepe.drove.models.api.ApiResponse;
import com.phonepe.drove.models.instance.InstanceInfo;
import com.phonepe.drove.models.instance.LocalInstanceInfo;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import java.util.List;
import java.util.Optional;

@Slf4j
public class LocalInstanceTracker {

    private final ObjectMapper mapper;
    private final DroveIgniteInstanceHelper droveIgniteInstanceHelper;

    private final String droveInstanceId;

    public LocalInstanceTracker(final DroveIgniteInstanceHelper droveIgniteInstanceHelper,
                                final ObjectMapper mapper) {
        this.mapper = mapper;
        this.droveIgniteInstanceHelper = droveIgniteInstanceHelper;
        this.droveInstanceId = System.getenv("DROVE_INSTANCE_ID");
    }

    public Optional<LocalInstanceInfo> getLocalInstanceInfo() {
        return droveIgniteInstanceHelper.findCurrentInstances(new LocalInstanceResponseTransformer());
    }

    private class LocalInstanceResponseTransformer implements DroveClient.ResponseHandler<Optional<LocalInstanceInfo>> {

        @Override
        public Optional<LocalInstanceInfo> defaultValue() {
            return Optional.empty();
        }

        @Override
        public Optional<LocalInstanceInfo> handle(final DroveClient.Response response) throws Exception {
            if (response.statusCode() != 200 || Strings.isNullOrEmpty(response.body())) {
                log.error("Could not find instances. Error: " + response.statusCode() + ": " + response.body());
                return Optional.empty();
            }

            val apiData = mapper.readValue(response.body(),
                    new TypeReference<ApiResponse<List<InstanceInfo>>>() {
                    });
            if (!apiData.getStatus().equals(ApiErrorCode.SUCCESS)) {
                log.error("Could not read instance list. Api call unsuccessful with error: " + apiData.getMessage());
                return Optional.empty();
            }

            log.info("Ignite Drove Response Data: " + apiData);
            return apiData.getData()
                    .stream()
                    .filter(instanceInfo -> instanceInfo.getInstanceId().equals(droveInstanceId))
                    .map(InstanceInfo::getLocalInfo)
                    .findAny();
        }
    }
}
