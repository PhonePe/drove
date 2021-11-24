package com.phonepe.drove.controller.resources;

import com.phonepe.drove.controller.event.DroveEvent;
import com.phonepe.drove.controller.event.DroveEventBus;
import com.phonepe.drove.controller.utils.StreamingSupport;
import lombok.extern.slf4j.Slf4j;
import org.glassfish.jersey.media.sse.SseFeature;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.sse.Sse;
import javax.ws.rs.sse.SseEventSink;

/**
 *
 */
@Path("/v1/events")
@Produces(SseFeature.SERVER_SENT_EVENTS)
@Slf4j
public class Events {

    private static StreamingSupport streamingSupport = null;

    @Inject
    public Events(DroveEventBus eventBus) {
        eventBus.onNewEvent().connect(this::handleNewEvent);
    }

    @GET
    public void generateEventStream(@Context SseEventSink eventSink, @Context Sse sse) {
        instance(sse).getSseBroadcaster().register(eventSink);
    }

    private static synchronized StreamingSupport instance(final Sse sse) {
        if (null == Events.streamingSupport) {
            Events.streamingSupport = new StreamingSupport(sse);
        }
        return streamingSupport;
    }


    private synchronized void handleNewEvent(DroveEvent event) {
        if (null == streamingSupport) {
            log.warn("Broadcaster not initialised");
            return;
        }
        streamingSupport.getSseBroadcaster().broadcast(
                streamingSupport.getEventBuilder()
                        .name(event.getType().name())
                        .id(event.getId())
                        .mediaType(MediaType.APPLICATION_JSON_TYPE)
                        .data(DroveEvent.class, event)
                        .reconnectDelay(3000)
                        .build());
    }

}
