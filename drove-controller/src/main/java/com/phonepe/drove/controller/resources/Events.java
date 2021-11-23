package com.phonepe.drove.controller.resources;

import com.phonepe.drove.controller.event.DroveEvent;
import com.phonepe.drove.controller.event.DroveEventBus;
import lombok.extern.slf4j.Slf4j;
import org.glassfish.jersey.media.sse.SseFeature;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.sse.OutboundSseEvent;
import javax.ws.rs.sse.Sse;
import javax.ws.rs.sse.SseBroadcaster;
import javax.ws.rs.sse.SseEventSink;

/**
 *
 */
@Path("/v1/events")
@Produces(SseFeature.SERVER_SENT_EVENTS)
@Slf4j
public class Events {

    private final DroveEventBus eventBus;

    private Sse sse;
    private SseBroadcaster sseBroadcaster = null;
    private OutboundSseEvent.Builder eventBuilder;

    @Inject
    public Events(DroveEventBus eventBus) {
        this.eventBus = eventBus;
        eventBus.onNewEvent().connect(this::handleNewEvent);
    }

    private void handleNewEvent(DroveEvent event) {
        if(null == sseBroadcaster) {
            log.warn("Broadcaster not initialised");
            return;
        }
        sseBroadcaster.broadcast(
                this.eventBuilder
                        .name(event.getType().name())
                        .id(event.getId())
                        .mediaType(MediaType.APPLICATION_JSON_TYPE)
                        .data(DroveEvent.class, event)
                        .reconnectDelay(3000)
                        .build());
    }


    @Context
    public void setSse(Sse sse) {
        this.sse = sse;
        this.eventBuilder = sse.newEventBuilder();
        this.sseBroadcaster = sse.newBroadcaster();
    }

    @GET
    public void generateEventStream(@Context SseEventSink eventSink, @Context Sse sse) {
        if(null == this.sse) {
            synchronized (this) {
                setSse(sse);
            }
            eventBus.onNewEvent().connect(this::handleNewEvent);
        }
        sseBroadcaster.register(eventSink);
    }
}
