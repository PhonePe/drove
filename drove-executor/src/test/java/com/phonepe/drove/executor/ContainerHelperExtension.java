package com.phonepe.drove.executor;

import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.Container;
import com.phonepe.drove.executor.engine.DockerLabels;
import com.phonepe.drove.executor.utils.ImagePullProgressHandler;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.util.List;

import static com.phonepe.drove.common.CommonTestUtils.*;
import static com.phonepe.drove.executor.ExecutorTestingUtils.DOCKER_CLIENT;

/**
 *
 */
@Slf4j
public class ContainerHelperExtension implements BeforeAllCallback, BeforeEachCallback, AfterEachCallback {

    @Override
    public void beforeAll(ExtensionContext extensionContext) throws Exception {

    }

    @Override
    public void beforeEach(ExtensionContext extensionContext) {
        killallTestContainers();
        ensureImage(APP_IMAGE_NAME);
        ensureImage(TASK_IMAGE_NAME);
    }

    @Override
    public void afterEach(ExtensionContext extensionContext) {
        killallTestContainers();
    }

    private void killallTestContainers() {
        val alreadyRunning = DOCKER_CLIENT.listContainersCmd()
                .exec()
                .stream()
                .filter(c -> c.getImage().equals(APP_IMAGE_NAME) || c.getImage().equals(TASK_IMAGE_NAME))
                .map(Container::getId)
                .toList();
        if (alreadyRunning.isEmpty()) {
            log.debug("No pre-existing containers running for image: {}", APP_IMAGE_NAME);
            return;
        }
        alreadyRunning.forEach(cid -> {
            DOCKER_CLIENT.stopContainerCmd(cid).exec();
            log.info("Stopped container: {}", cid);
        });
        waitUntil(() -> DOCKER_CLIENT.listContainersCmd()
                .withLabelFilter(List.of(DockerLabels.DROVE_INSTANCE_ID_LABEL,
                                         DockerLabels.DROVE_INSTANCE_SPEC_LABEL,
                                         DockerLabels.DROVE_INSTANCE_DATA_LABEL))
                .exec()
                .isEmpty());
    }

    private void ensureImage(final String imageName) {
        try {
            val imgDetails = DOCKER_CLIENT.inspectImageCmd(imageName)
                    .exec();
            log.info("Image {} already present with id {}", imageName, imgDetails.getId());
            return;
        }
        catch (NotFoundException e) {
            log.info("Ensuring docker image {} exists", imageName);
        }
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