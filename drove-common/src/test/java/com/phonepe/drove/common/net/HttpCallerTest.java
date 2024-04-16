package com.phonepe.drove.common.net;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.phonepe.drove.models.common.HTTPCallSpec;
import com.phonepe.drove.models.common.Protocol;
import lombok.SneakyThrows;
import lombok.val;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 *
 */
@WireMockTest
class HttpCallerTest {
    @Test
    @SneakyThrows
    void testSimpleConfigGet(final WireMockRuntimeInfo wm) {
        stubFor(get("/config/v1/myapp").willReturn(ok("name=John Doe")));

        val spec = HTTPCallSpec.builder()
                .protocol(Protocol.HTTP)
                .hostname("localhost")
                .port(wm.getHttpPort())
                .path("/config/v1/myapp")
                .build();
        try(val client = HttpClients.createDefault()) {
            val fetcher = new HttpCaller(client);
            val response = new String(fetcher.execute(spec));
            assertEquals("name=John Doe", response);
        }
    }
}