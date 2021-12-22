package com.phonepe.drove.executor;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.zerodep.ZerodepDockerHttpClient;
import com.phonepe.drove.common.AbstractTestBase;
import com.phonepe.drove.executor.statemachine.actions.ImagePullProgressHandler;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.junit.jupiter.api.BeforeAll;

import java.net.URI;

/**
 *
 */
@Slf4j
public class AbstractExecutorTestBase extends AbstractTestBase {
    protected static final DockerClient DOCKER_CLIENT
            = DockerClientImpl.getInstance(DefaultDockerClientConfig.createDefaultConfigBuilder().build(),
                                           new ZerodepDockerHttpClient.Builder()
                                                   .dockerHost(URI.create("unix:///var/run/docker.sock"))
                                                   .build());

    @BeforeAll
    static void ensureDockerImage() {
        val imageName = ExecutorTestingUtils.IMAGE_NAME;
        log.info("Ensuring docker image {} exists", imageName);
        try {
            DOCKER_CLIENT.pullImageCmd(imageName)
                    .exec(new ImagePullProgressHandler(imageName))
                    .awaitCompletion();
        }
        catch (InterruptedException e) {
            log.info("Image pull has been interrupted");
            Thread.currentThread().interrupt();
        }
        log.debug("Docker image {} has been fetched", imageName);
    }
}
