package com.phonepe.drove.eventslistener;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.phonepe.drove.client.DroveClient;
import com.phonepe.drove.models.api.ApiErrorCode;
import com.phonepe.drove.models.api.ApiResponse;
import com.phonepe.drove.models.events.DroveEvent;
import io.appform.signals.signals.ConsumingFireForgetSignal;
import io.appform.signals.signals.ScheduledSignal;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Objects;

/**
 * Streams remote events. Invokes signal with received events. Uses {@link DroveEventPollingOffsetStore} to store and
 * retrieve last sync time. Default poll is at 10 seconds interval and default storage is in-memory.
 */
@SuppressWarnings("rawtypes")
@Slf4j
public class DroveRemoteEventListener implements AutoCloseable {
    private static final String POLLER_NAME = "EVENT_POLLER";

    private final DroveClient droveClient;
    private final ObjectMapper mapper;
    private final DroveEventPollingOffsetStore offsetStore;

    private final ConsumingFireForgetSignal<List<DroveEvent>> eventReceived = new ConsumingFireForgetSignal<>();
    private final ScheduledSignal checkForEventSignal;

    public DroveRemoteEventListener(DroveClient droveClient, ObjectMapper mapper) {
        this(droveClient, mapper, new DroveEventPollingOffsetInMemoryStore(), Duration.ofSeconds(10));
    }

    @Builder
    public DroveRemoteEventListener(
            DroveClient droveClient,
            ObjectMapper mapper,
            DroveEventPollingOffsetStore offsetStore,
            final Duration pollInterval) {
        this.droveClient = droveClient;
        this.mapper = mapper;
        this.offsetStore = offsetStore;
        checkForEventSignal = new ScheduledSignal(pollInterval);
        mapper.registerModule(new SimpleModule()
                                      .addDeserializer(DroveEvent.class, new DroveEventDeserializer(mapper)));
    }

    public ConsumingFireForgetSignal<List<DroveEvent>> onEventReceived() {
        return eventReceived;
    }

    public void start() {
        checkForEventSignal.connect(POLLER_NAME, this::checkForEvents);
    }

    private void checkForEvents(Date triggerTime) {
        try {
            val request = new DroveClient.Request(DroveClient.Method.GET,
                                                  "/apis/v1/cluster/events?lastSyncTime=" + offsetStore.getLastOffset());
            val time = Instant.now();
            val response = droveClient.execute(request);
            if (null == response) {
                log.warn("Received no response from drove");
            }
            else {
                if (response.statusCode() == 200) {
                    val apiResponse = mapper.readValue(response.body(),
                                                       new TypeReference<ApiResponse<List<DroveEvent>>>() {
                                                       });

                    if (apiResponse.getStatus().equals(ApiErrorCode.SUCCESS)) {
                        val newEvents = Objects.<List<DroveEvent>>requireNonNullElse(apiResponse.getData(), List.of());
                        if (!newEvents.isEmpty()) {
                            eventReceived.dispatch(newEvents);
                        }
                        else {
                            log.debug("No new event received from Drove");
                        }
                        offsetStore.setLastOffset(time.toEpochMilli());
                        return;
                    }
                    log.error("Error reading event list. Error message: {}:{}",
                              apiResponse.getStatus(), apiResponse.getMessage());
                }
            }
        }
        catch (Exception e) {
            log.error("Error reading drove events: " + e.getMessage(), e);
        }
    }

    @Override
    public void close() {
        checkForEventSignal.disconnect(POLLER_NAME);
        checkForEventSignal.close();
    }
}
