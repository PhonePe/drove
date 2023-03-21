package com.phonepe.drove.eventslistener;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.phonepe.drove.client.DroveClient;
import com.phonepe.drove.client.DroveClientConfig;
import com.phonepe.drove.client.transport.basic.DroveHttpNativeTransport;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 *
 */
@Slf4j
@WireMockTest
class DroveRemoteEventListenerTest {

    @Test
    @SneakyThrows
    void listenerTestSuccess(final WireMockRuntimeInfo wm) {
        val clientConfig = new DroveClientConfig(List.of(wm.getHttpBaseUrl()),
                                                 Duration.ofSeconds(30),
                                                 Duration.ofSeconds(2),
                                                 Duration.ofSeconds(2));
        val json = Files.readString(Paths.get(Objects.requireNonNull(
                getClass().getClassLoader().getResource("events-response.json")).toURI()));

        stubFor(get("/apis/v1/cluster/events?lastSyncTime=0").willReturn(ok(json)));
        val client = new DroveClient(clientConfig,
                                     List.of(), new DroveHttpNativeTransport(clientConfig));
        val mapper = new ObjectMapper();
        mapper.registerModule(new ParameterNamesModule());
        mapper.setSerializationInclusion(JsonInclude.Include.NON_EMPTY);
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        mapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        mapper.enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS);
        val listener = new DroveRemoteEventListener(client,
                                                    mapper,
                                                    new DroveEventPollingOffsetInMemoryStore(),
                                                    Duration.ofSeconds(1));
        val ctr = new AtomicLong();
        listener.onEventReceived().connect(events -> {
            events.forEach(event -> log.info("Event type: {}", event.getType()));
            ctr.addAndGet(events.size());
        });

        listener.start();

        Awaitility.await()
                .atMost(Duration.ofMinutes(1))
                .until(() -> ctr.get() > 0);
        assertEquals(33, ctr.get());
        listener.close();
    }

    @Test
    @SneakyThrows
    void testNoEvent() {
        val droveClient = mock(DroveClient.class);
        val called = new AtomicBoolean();
        when(droveClient.execute(ArgumentMatchers.any()))
                .thenAnswer(invocationOnMock -> {
                    called.set(true);
                    return new DroveClient.Response(200, Map.of(), "{\"status\": \"SUCCESS\"}");
                });

        val mapper = new ObjectMapper();
        mapper.registerModule(new ParameterNamesModule());
        mapper.setSerializationInclusion(JsonInclude.Include.NON_EMPTY);
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        mapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        mapper.enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS);
        val listener = new DroveRemoteEventListener(droveClient,
                                                    mapper,
                                                    new DroveEventPollingOffsetInMemoryStore(),
                                                    Duration.ofSeconds(1));
        val ctr = new AtomicLong();
        listener.onEventReceived().connect(events -> {
            events.forEach(event -> log.info("Event type: {}", event.getType()));
            ctr.addAndGet(events.size());
        });

        listener.start();

        Awaitility.await()
                .atMost(Duration.ofMinutes(1))
                .until(called::get);
        listener.close();
    }

    @Test
    @SneakyThrows
    void testNoResponse() {
        val droveClient = mock(DroveClient.class);
        val called = new AtomicBoolean();
        when(droveClient.execute(ArgumentMatchers.any()))
                .thenAnswer(invocationOnMock -> {
                    called.set(true);
                    return null;
                });

        val mapper = new ObjectMapper();
        mapper.registerModule(new ParameterNamesModule());
        mapper.setSerializationInclusion(JsonInclude.Include.NON_EMPTY);
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        mapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        mapper.enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS);
        val listener = new DroveRemoteEventListener(droveClient,
                                                    mapper,
                                                    new DroveEventPollingOffsetInMemoryStore(),
                                                    Duration.ofSeconds(1));
        val ctr = new AtomicLong();
        listener.onEventReceived().connect(events -> {
            events.forEach(event -> log.info("Event type: {}", event.getType()));
            ctr.addAndGet(events.size());
        });

        listener.start();

        Awaitility.await()
                .atMost(Duration.ofMinutes(1))
                .until(called::get);
        listener.close();
    }

    @Test
    @SneakyThrows
    void testThrow() {
        val droveClient = mock(DroveClient.class);
        val called = new AtomicBoolean();
        when(droveClient.execute(ArgumentMatchers.any()))
                .thenAnswer(invocationOnMock -> {
                    called.set(true);
                    throw new RuntimeException("Test exception");
                });

        val mapper = new ObjectMapper();
        mapper.registerModule(new ParameterNamesModule());
        mapper.setSerializationInclusion(JsonInclude.Include.NON_EMPTY);
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        mapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        mapper.enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS);
        val listener = new DroveRemoteEventListener(droveClient,
                                                    mapper,
                                                    new DroveEventPollingOffsetInMemoryStore(),
                                                    Duration.ofSeconds(1));
        val ctr = new AtomicLong();
        listener.onEventReceived().connect(events -> {
            events.forEach(event -> log.info("Event type: {}", event.getType()));
            ctr.addAndGet(events.size());
        });

        listener.start();

        Awaitility.await()
                .atMost(Duration.ofMinutes(1))
                .until(called::get);
        listener.close();
    }

    @Test
    @SneakyThrows
    void testFail() {
        val droveClient = mock(DroveClient.class);
        val called = new AtomicBoolean();
        when(droveClient.execute(ArgumentMatchers.any()))
                .thenAnswer(invocationOnMock -> {
                    called.set(true);
                    return new DroveClient.Response(200, Map.of(), "{\"status\": \"FAILED\", \"message\": \"Blah\"}");
                });

        val mapper = new ObjectMapper();
        mapper.registerModule(new ParameterNamesModule());
        mapper.setSerializationInclusion(JsonInclude.Include.NON_EMPTY);
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        mapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        mapper.enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS);
        val listener = new DroveRemoteEventListener(droveClient,
                                                    mapper,
                                                    new DroveEventPollingOffsetInMemoryStore(),
                                                    Duration.ofSeconds(1));
        val ctr = new AtomicLong();
        listener.onEventReceived().connect(events -> {
            events.forEach(event -> log.info("Event type: {}", event.getType()));
            ctr.addAndGet(events.size());
        });

        listener.start();

        Awaitility.await()
                .atMost(Duration.ofMinutes(1))
                .until(called::get);
        listener.close();
    }

    @Test
    void testBuild() {
        {
            try (val r = DroveRemoteEventListener.builder().build()) {
                fail("Should have failed with npe");
            }
            catch (Exception e) {
                if (e instanceof NullPointerException n) {
                    assertEquals("Please provide drove client", n.getMessage());
                }
                else {
                    fail("Should have failed with npe");
                }
            }
        }
        val dc = mock(DroveClient.class);
        {
            try (val r = DroveRemoteEventListener.builder().droveClient(dc).build()) {
                fail("Should have failed with npe");
            }
            catch (Exception e) {
                if (e instanceof NullPointerException n) {
                    assertEquals("Please provide object mapper", n.getMessage());
                }
                else {
                    fail("Should have failed with npe");
                }
            }
        }
        {
            try (val r = DroveRemoteEventListener.builder()
                    .droveClient(dc)
                    .mapper(new ObjectMapper())
                    .build()) {
                //Nothing to do here
            }
            catch (Exception e) {
                    fail("Should not have failed with: " + e.getMessage());
            }
        }
    }
}