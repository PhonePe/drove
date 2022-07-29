package com.phonepe.drove.controller.utils;

import lombok.Value;

import javax.ws.rs.sse.OutboundSseEvent;
import javax.ws.rs.sse.Sse;
import javax.ws.rs.sse.SseBroadcaster;

/**
 *
 */
@Value
public class StreamingSupport {
    Sse sse;
    SseBroadcaster sseBroadcaster;
    OutboundSseEvent.Builder eventBuilder;

    public StreamingSupport(Sse sse) {
        this.sse = sse;
        this.sseBroadcaster = this.sse.newBroadcaster();
        this.eventBuilder = this.sse.newEventBuilder();
    }
}
