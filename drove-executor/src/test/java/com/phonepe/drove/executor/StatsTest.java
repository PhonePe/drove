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

    /*
    available: 2 nodes (0-1)
    node 0 cpus: 0 2 4 6 8 10 12 14 16 18 20 22 24 26 28 30 32 34 36 38 40 42 44 46 48 50 52 54 56 58 60 62 64 66 68 70 72 74 76 78
    node 0 size: 288766 MB
    node 0 free: 194045 MB
    node 1 cpus: 1 3 5 7 9 11 13 15 17 19 21 23 25 27 29 31 33 35 37 39 41 43 45 47 49 51 53 55 57 59 61 63 65 67 69 71 73 75 77 79
    node 1 size: 290269 MB
    node 1 free: 200774 MB
    node distances:
    node   0   1
      0:  10  21
      1:  21  10
     */
    @Test
    @SneakyThrows
    void readResource() {

    }
}
