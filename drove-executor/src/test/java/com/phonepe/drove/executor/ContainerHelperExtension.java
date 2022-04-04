package com.phonepe.drove.executor;

import com.github.dockerjava.api.model.Container;
import com.phonepe.drove.executor.statemachine.actions.ImagePullProgressHandler;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import static com.phonepe.drove.executor.ExecutorTestingUtils.DOCKER_CLIENT;
import static com.phonepe.drove.common.CommonTestUtils.IMAGE_NAME;

/**
 *
 */
@Slf4j
public class ContainerHelperExtension implements BeforeAllCallback, BeforeEachCallback, AfterEachCallback {

    @Override
    public void beforeAll(ExtensionContext extensionContext) throws Exception {
        log.info("Ensuring docker image {} exists", IMAGE_NAME);
        try {
            DOCKER_CLIENT.pullImageCmd(IMAGE_NAME)
                    .exec(new ImagePullProgressHandler(IMAGE_NAME))
                    .awaitCompletion();
        }
        catch (InterruptedException e) {
            log.info("Image pull has been interrupted");
            Thread.currentThread().interrupt();
        }
        log.debug("Docker image {} has been fetched", IMAGE_NAME);
    }

    @Override
    public void beforeEach(ExtensionContext extensionContext) throws Exception {
        killallTestContainers();

    }

    @Override
    public void afterEach(ExtensionContext extensionContext) throws Exception {
        killallTestContainers();
    }

    private void killallTestContainers() {
        val alreadyRunning = DOCKER_CLIENT.listContainersCmd()
                .exec()
                .stream()
                .filter(c -> c.getImage().equals(IMAGE_NAME))
                .map(Container::getId)
                .toList();
        if(alreadyRunning.isEmpty()) {
            log.debug("No pre-existing containers running for image: {}", IMAGE_NAME);
            return;
        }
        alreadyRunning.forEach(cid -> {
                            DOCKER_CLIENT.stopContainerCmd(cid).exec();
                            log.info("Stopped container: {}", cid);
                        });
    }
}
