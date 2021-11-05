package com.phonepe.drove.executor.engine;

import com.evanlennick.retry4j.CallExecutorBuilder;
import com.evanlennick.retry4j.config.RetryConfigBuilder;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.model.PullResponseItem;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.zerodep.ZerodepDockerHttpClient;
import com.phonepe.drove.common.net.Communicator;
import com.phonepe.drove.common.model.InstanceSpec;
import com.phonepe.drove.common.model.StopInstanceMessage2;
import com.phonepe.drove.models.application.executable.DockerCoordinates;
import lombok.val;

import java.io.Closeable;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 *
 */
public class DockerExecutionEngine implements ExecutionEngine {
    private DockerClient client;

    public DockerExecutionEngine() {
        client = DockerClientImpl.getInstance(DefaultDockerClientConfig.createDefaultConfigBuilder()
                                                      .build(),
                                              new ZerodepDockerHttpClient.Builder()
                                                      .dockerHost(URI.create("unix:///var/run/docker.sock"))
                                                      .build());
    }

    @Override
    public void startContainer(
            InstanceSpec startInstanceMessage, Communicator communicator) {
        val image = startInstanceMessage.getExecutable().accept(DockerCoordinates::getUrl);
        client.listContainersCmd().exec()
                .forEach(System.out::println);
        AtomicBoolean completed = new AtomicBoolean();
        val r = client.pullImageCmd(image)
                .exec(new ResultCallback<PullResponseItem>() {
                    @Override
                    public void onStart(Closeable closeable) {
                        System.out.println("Started");
                    }

                    @Override
                    public void onNext(PullResponseItem object) {
                        var progressDetail = object.getProgressDetail();
                        System.out.println(object.getStatus());
                        if (null != progressDetail) {
                            System.out.println(progressDetail);
                        }
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        throwable.printStackTrace();
                    }

                    @Override
                    public void onComplete() {
                        System.out.println("completed");
                        completed.set(true);
                    }

                    @Override
                    public void close() throws IOException {

                    }
                });
        val retryConfig = new RetryConfigBuilder()
                .withFixedBackoff()
                .withDelayBetweenTries(Duration.ofSeconds(1))
                .retryOnReturnValue(false)
                .withMaxNumberOfTries(100)
                .build();
        val status = new CallExecutorBuilder().config(retryConfig).build().execute(() -> completed.get());
        System.out.println("Done: " + status);
        try (val containerCmd = client.createContainerCmd(UUID.randomUUID().toString())) {
            val id = containerCmd
                    .withImage(image)
                    .withName(startInstanceMessage.getAppId() + UUID.randomUUID())
                    .exec()
                    .getId();
            System.out.println("Container id: " + id);
            client.startContainerCmd(id)
                    .exec();

        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void stopContainer(
            StopInstanceMessage2 startInstanceMessage, Communicator communicator) {

    }

    @Override
    public void getContainerInfo(
            StopInstanceMessage2 startInstanceMessage, Communicator communicator) {

    }
}
