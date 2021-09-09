package com.phonepe.drove.executor;

import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;

/**
 *
 */
class StatsTest {
    @Test
    @SneakyThrows
    void test() {
        /*val client = DockerClientImpl.getInstance(DefaultDockerClientConfig.createDefaultConfigBuilder()
                                                          .build(),
                                                  new ZerodepDockerHttpClient.Builder()
                                                          .dockerHost(URI.create("unix:///var/run/docker.sock"))
                                                          .build());
        val mapper = new ObjectMapper();
        CommonUtils.configureMapper(mapper);
        val container = client.listContainersCmd()
                .withLabelFilter(Collections.singletonMap(DockerLabels.DROVE_INSTANCE_ID_LABEL,
                                                          "7d26e732-79e0-4861-9049-c5464ce2ce2b"))
                .exec()
                .stream()
                .findAny()
                .orElse(null);
        assertNotNull(container);
        client.statsCmd(container.getId())
                .withNoStream(true)
                .exec(new ResultCallback.Adapter<>() {

                    @Override
                    @SneakyThrows
                    public void onNext(Statistics object) {
                        if (null != object) {
                            System.out.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(object));
                        }
                    }
                })
                .awaitCompletion();*/
    }
}
