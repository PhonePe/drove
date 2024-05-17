package com.phonepe.drove.common.net;

import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.phonepe.drove.common.CommonUtils;
import com.phonepe.drove.models.common.HTTPCallSpec;
import com.phonepe.drove.models.common.HTTPVerb;
import com.phonepe.drove.models.common.Protocol;
import lombok.SneakyThrows;
import lombok.val;
import org.apache.hc.core5.http.HttpHeaders;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 *
 */
@WireMockTest(httpsEnabled = true)
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
                .insecure(false)
                .build();
        try (val client = CommonUtils.createHttpClient(false);
             val insecureClient = CommonUtils.createHttpClient(true)) {
            val fetcher = new HttpCaller(client, insecureClient);
            val response = new String(fetcher.execute(spec));
            assertEquals("name=John Doe", response);
        }
    }

    @Test
    @SneakyThrows
    void testSimpleConfigGetHttps(final WireMockRuntimeInfo wm) {
        stubFor(get("/config/v1/myapp").willReturn(ok("name=John Doe")));

        val spec = HTTPCallSpec.builder()
                .protocol(Protocol.HTTPS)
                .hostname("localhost")
                .port(wm.getHttpsPort())
                .path("/config/v1/myapp")
                .insecure(true)
                .build();
        try (val client = CommonUtils.createHttpClient(false);
             val insecureClient = CommonUtils.createHttpClient(true)) {
            val fetcher = new HttpCaller(client, insecureClient);
            val response = new String(fetcher.execute(spec));
            assertEquals("name=John Doe", response);
        }
    }

    @Test
    @SneakyThrows
    void testSimpleConfigGetIOException(final WireMockRuntimeInfo wm) {
        stubFor(get("/config/v1/myapp")
                        .willReturn(aResponse().withFault(Fault.MALFORMED_RESPONSE_CHUNK)));

        val spec = HTTPCallSpec.builder()
                .protocol(Protocol.HTTP)
                .hostname("localhost")
                .port(wm.getHttpPort())
                .path("/config/v1/myapp")
                .insecure(false)
                .build();
        try (val client = CommonUtils.createHttpClient(false);
             val insecureClient = CommonUtils.createHttpClient(true)) {
            val fetcher = new HttpCaller(client, insecureClient);
            assertThrows(IllegalStateException.class, () -> fetcher.execute(spec));
        }
    }

    @Test
    @SneakyThrows
    void testSimpleConfigPost(final WireMockRuntimeInfo wm) {
        stubFor(post("/config/v1/myapp")
                        .withRequestBody(equalTo("Test Body"))
                        .willReturn(ok("name=John Doe")));

        val spec = HTTPCallSpec.builder()
                .protocol(Protocol.HTTP)
                .hostname("localhost")
                .port(wm.getHttpPort())
                .path("/config/v1/myapp")
                .insecure(false)
                .verb(HTTPVerb.POST)
                .payload("Test Body")
                .build();
        try (val client = CommonUtils.createHttpClient(false);
             val insecureClient = CommonUtils.createHttpClient(true)) {
            val fetcher = new HttpCaller(client, insecureClient);
            val response = new String(fetcher.execute(spec));
            assertEquals("name=John Doe", response);
        }
    }

    @Test
    @SneakyThrows
    void testSimpleConfigPostNoBody(final WireMockRuntimeInfo wm) {
        stubFor(post("/config/v1/myapp")
                        .willReturn(ok("name=John Doe")));

        val spec = HTTPCallSpec.builder()
                .protocol(Protocol.HTTP)
                .hostname("localhost")
                .port(wm.getHttpPort())
                .path("/config/v1/myapp")
                .insecure(false)
                .verb(HTTPVerb.POST)
                .build();
        try (val client = CommonUtils.createHttpClient(false);
             val insecureClient = CommonUtils.createHttpClient(true)) {
            val fetcher = new HttpCaller(client, insecureClient);
            val response = new String(fetcher.execute(spec));
            assertEquals("name=John Doe", response);
        }
    }

    @Test
    @SneakyThrows
    void testSimpleConfigPut(final WireMockRuntimeInfo wm) {
        stubFor(put("/config/v1/myapp")
                        .withRequestBody(equalTo("Test Body"))
                        .willReturn(ok("name=John Doe")));

        val spec = HTTPCallSpec.builder()
                .protocol(Protocol.HTTP)
                .hostname("localhost")
                .port(wm.getHttpPort())
                .path("/config/v1/myapp")
                .insecure(false)
                .verb(HTTPVerb.PUT)
                .payload("Test Body")
                .build();
        try (val client = CommonUtils.createHttpClient(false);
             val insecureClient = CommonUtils.createHttpClient(true)) {
            val fetcher = new HttpCaller(client, insecureClient);
            val response = new String(fetcher.execute(spec));
            assertEquals("name=John Doe", response);
        }
    }

    @Test
    @SneakyThrows
    void testSimpleConfigPutNoBody(final WireMockRuntimeInfo wm) {
        stubFor(put("/config/v1/myapp")
                        .willReturn(ok("name=John Doe")));

        val spec = HTTPCallSpec.builder()
                .protocol(Protocol.HTTP)
                .hostname("localhost")
                .port(wm.getHttpPort())
                .path("/config/v1/myapp")
                .insecure(false)
                .verb(HTTPVerb.PUT)
                .build();
        try (val client = CommonUtils.createHttpClient(false);
             val insecureClient = CommonUtils.createHttpClient(true)) {
            val fetcher = new HttpCaller(client, insecureClient);
            val response = new String(fetcher.execute(spec));
            assertEquals("name=John Doe", response);
        }
    }

    @Test
    @SneakyThrows
    void testSimpleConfigBasicAuth(final WireMockRuntimeInfo wm) {
        stubFor(get("/config/v1/myapp")
                        .withBasicAuth("testuser", "testpassword")
                        .willReturn(ok("name=John Doe")));

        val spec = HTTPCallSpec.builder()
                .protocol(Protocol.HTTP)
                .hostname("localhost")
                .port(wm.getHttpPort())
                .path("/config/v1/myapp")
                .username("testuser")
                .password("testpassword")
                .verb(HTTPVerb.GET)
                .insecure(false)
                .build();
        try (val client = CommonUtils.createHttpClient(false);
             val insecureClient = CommonUtils.createHttpClient(true)) {
            val fetcher = new HttpCaller(client, insecureClient);
            val response = new String(fetcher.execute(spec));
            assertEquals("name=John Doe", response);
        }
    }

    @Test
    @SneakyThrows
    void testSimpleConfigBasicAuthFail(final WireMockRuntimeInfo wm) {
        stubFor(get("/config/v1/myapp")
                        .withBasicAuth("testuser", "testpassword")
                        .willReturn(ok("name=John Doe")));

        val spec = HTTPCallSpec.builder()
                .protocol(Protocol.HTTP)
                .hostname("localhost")
                .port(wm.getHttpPort())
                .path("/config/v1/myapp")
                .username("testuser")
                .password("wrongpassword")
                .verb(HTTPVerb.GET)
                .insecure(false)
                .build();
        try (val client = CommonUtils.createHttpClient(false);
             val insecureClient = CommonUtils.createHttpClient(true)) {
            val fetcher = new HttpCaller(client, insecureClient);
            assertThrows(IllegalStateException.class,
                         () -> fetcher.execute(spec));
        }
    }

    @Test
    @SneakyThrows
    void testSimpleConfigHeaderAuth(final WireMockRuntimeInfo wm) {
        stubFor(get("/config/v1/myapp")
                        .withHeader(HttpHeaders.AUTHORIZATION, equalTo("Token"))
                        .willReturn(ok("name=John Doe")));

        val spec = HTTPCallSpec.builder()
                .protocol(Protocol.HTTP)
                .hostname("localhost")
                .port(wm.getHttpPort())
                .path("/config/v1/myapp")
                .authHeader("Token")
                .verb(HTTPVerb.GET)
                .insecure(false)
                .build();
        try (val client = CommonUtils.createHttpClient(false);
             val insecureClient = CommonUtils.createHttpClient(true)) {
            val fetcher = new HttpCaller(client, insecureClient);
            val response = new String(fetcher.execute(spec));
            assertEquals("name=John Doe", response);
        }
    }
}