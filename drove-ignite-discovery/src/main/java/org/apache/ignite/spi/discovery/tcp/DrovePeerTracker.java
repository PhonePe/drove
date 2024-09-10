package org.apache.ignite.spi.discovery.tcp;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Strings;
import com.phonepe.drove.client.DroveClient;
import com.phonepe.drove.ignite.discovery.DroveIgniteInstanceHelper;
import com.phonepe.drove.models.api.ApiErrorCode;
import com.phonepe.drove.models.api.ApiResponse;
import com.phonepe.drove.models.instance.InstanceInfo;
import com.phonepe.drove.models.instance.InstancePort;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Slf4j
public class DrovePeerTracker {
    private final DroveIgniteInstanceHelper droveIgniteInstanceHelper;
    private final ObjectMapper mapper;
    private final String portName;

    @SneakyThrows
    public DrovePeerTracker(final DroveIgniteInstanceHelper droveIgniteInstanceHelper,
                            final String portName,
                            final ObjectMapper mapper) {
        this.mapper = mapper;
        this.portName = portName;
        this.droveIgniteInstanceHelper = droveIgniteInstanceHelper;
    }

    public Collection<InetSocketAddress> peers() {
        return droveIgniteInstanceHelper.findCurrentInstances(new DrovePeerTracker.PeerResponseTransformer()).orElse(List.of());
    }

    private class PeerResponseTransformer implements DroveClient.ResponseHandler<Optional<Collection<InetSocketAddress>>> {
        @Override
        public Optional<Collection<InetSocketAddress>> defaultValue() {
            return Optional.empty();
        }

        @Override
        public Optional<Collection<InetSocketAddress>> handle(DroveClient.Response response) throws Exception {
            if (response.statusCode() != 200 || Strings.isNullOrEmpty(response.body())) {
                log.error("Could not find peers. Error: " + response.statusCode() + ": " + response.body());
                return Optional.empty();
            }
            val apiData = mapper.readValue(response.body(),
                    new TypeReference<ApiResponse<List<InstanceInfo>>>() {
                    });
            if (!apiData.getStatus().equals(ApiErrorCode.SUCCESS)) {
                log.error("Could not read peer list. Api call unsuccessful with error: " + apiData.getMessage());
                return Optional.empty();
            }
            log.info("IgnitePeerTracker Drove Response Data: " + apiData);
            return Optional.of(apiData.getData()
                    .stream()
                    .map(this::translate)
                    .filter(Objects::nonNull)
                    .toList());
        }

        private InetSocketAddress translate(InstanceInfo info) {
            val hostname = info.getLocalInfo().getHostname();
            val portInfo =
                    Objects.requireNonNullElse(info.getLocalInfo()
                                            .getPorts(),
                                    Map.<String, InstancePort>of())
                            .get(portName);
            if (null == portInfo) {
                log.error("No port found with port name: " + portName + " on app instance " + info.getInstanceId());
                return null;
            }
            return new InetSocketAddress(hostname, portInfo.getHostPort());
        }
    }
}

