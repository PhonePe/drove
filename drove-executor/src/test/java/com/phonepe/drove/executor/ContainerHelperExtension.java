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

package com.phonepe.drove.executor;

import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.exception.NotModifiedException;
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
            try {
                DOCKER_CLIENT.stopContainerCmd(cid).exec();
                log.info("Stopped container: {}", cid);
            }
            catch (NotFoundException | NotModifiedException e) {
                log.info("Container {} has already been stopped.", cid);
            }
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
                    .exec(new ImagePullProgressHandler(null, imageName))
                    .awaitCompletion();
        }
        catch (InterruptedException e) {
            log.info("Image pull has been interrupted");
            Thread.currentThread().interrupt();
        }
        log.debug("Docker image {} has been fetched", imageName);
    }
}
