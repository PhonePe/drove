package com.phonepe.drove.client.transport.httpcomponent;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.phonepe.drove.client.DroveClient;
import com.phonepe.drove.client.DroveClientConfig;
import com.phonepe.drove.client.DroveHttpTransport;
import lombok.val;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

/**
 *
 */
@WireMockTest
class DroveHttpComponentsTransportTest {
    @Test
    void testGet(WireMockRuntimeInfo wm) {
        stubFor(get(DroveClient.PING_API)
                        .withHeader("TestHeader", equalTo("TestValue"))
                        .willReturn(ok()));
        try(val t = new DroveHttpComponentsTransport(clientConfig(wm))) {
            Assertions.assertTrue(
                    t.execute(new DroveHttpTransport.TransportRequest(DroveClient.Method.GET,
                                                                      URI.create(wm.getHttpBaseUrl() + DroveClient.PING_API),
                                                                      Map.of("TestHeader", List.of("TestValue")),
                                                                      null),
                              new DroveClient.ResponseHandler<Boolean>() {
                                  @Override
                                  public Boolean defaultValue() {
                                      return false;
                                  }

                                  @Override
                                  public Boolean handle(DroveClient.Response response) {
                                      return response.statusCode() == 200;
                                  }
                              }));
            Assertions.assertFalse(
                    t.execute(new DroveHttpTransport.TransportRequest(DroveClient.Method.GET,
                                                                      URI.create(wm.getHttpBaseUrl() + DroveClient.PING_API),
                                                                      Map.of("TestHeader", List.of("TestValue")),
                                                                      null),
                              new DroveClient.ResponseHandler<Boolean>() {
                                  @Override
                                  public Boolean defaultValue() {
                                      return false;
                                  }

                                  @Override
                                  public Boolean handle(DroveClient.Response response) {
                                      throw new IllegalStateException("Test failure");
                                  }
                              }));
        }
    }

    @Test
    void testPost(WireMockRuntimeInfo wm) {
        stubFor(post(DroveClient.PING_API)
                        .withHeader("TestHeader", equalTo("TestValue"))
                        .withRequestBody(equalTo("TestBody"))
                        .willReturn(ok()));
        try(val t = new DroveHttpComponentsTransport(clientConfig(wm))) {
            Assertions.assertTrue(
                    t.execute(new DroveHttpTransport.TransportRequest(DroveClient.Method.POST,
                                                                      URI.create(wm.getHttpBaseUrl() + DroveClient.PING_API),
                                                                      Map.of("TestHeader", List.of("TestValue")),
                                                                      "TestBody"),
                              new DroveClient.ResponseHandler<Boolean>() {
                                  @Override
                                  public Boolean defaultValue() {
                                      return false;
                                  }

                                  @Override
                                  public Boolean handle(DroveClient.Response response) {
                                      return response.statusCode() == 200;
                                  }
                              }));
            Assertions.assertFalse(
                    t.execute(new DroveHttpTransport.TransportRequest(DroveClient.Method.POST,
                                                                      URI.create(wm.getHttpBaseUrl() + DroveClient.PING_API),
                                                                      Map.of("TestHeader", List.of("TestValue")),
                                                                      "TestBody"),
                              new DroveClient.ResponseHandler<Boolean>() {
                                  @Override
                                  public Boolean defaultValue() {
                                      return false;
                                  }

                                  @Override
                                  public Boolean handle(DroveClient.Response response) {
                                      throw new IllegalStateException("Test failure");
                                  }
                              }));
        }
    }

    @Test
    void testPut(WireMockRuntimeInfo wm) {
        stubFor(put(DroveClient.PING_API)
                        .withHeader("TestHeader", equalTo("TestValue"))
                        .withRequestBody(equalTo("TestBody"))
                        .willReturn(ok()));
        try(val t = new DroveHttpComponentsTransport(clientConfig(wm))) {
            Assertions.assertTrue(
                    t.execute(new DroveHttpTransport.TransportRequest(DroveClient.Method.PUT,
                                                                      URI.create(wm.getHttpBaseUrl() + DroveClient.PING_API),
                                                                      Map.of("TestHeader", List.of("TestValue")),
                                                                      "TestBody"),
                              new DroveClient.ResponseHandler<Boolean>() {
                                  @Override
                                  public Boolean defaultValue() {
                                      return false;
                                  }

                                  @Override
                                  public Boolean handle(DroveClient.Response response) {
                                      return response.statusCode() == 200;
                                  }
                              }));
            Assertions.assertFalse(
                    t.execute(new DroveHttpTransport.TransportRequest(DroveClient.Method.PUT,
                                                                      URI.create(wm.getHttpBaseUrl() + DroveClient.PING_API),
                                                                      Map.of("TestHeader", List.of("TestValue")),
                                                                      "TestBody"),
                              new DroveClient.ResponseHandler<Boolean>() {
                                  @Override
                                  public Boolean defaultValue() {
                                      return false;
                                  }

                                  @Override
                                  public Boolean handle(DroveClient.Response response) {
                                      throw new IllegalStateException("Test failure");
                                  }
                              }));
        }
    }

    @Test
    void testDelete(WireMockRuntimeInfo wm) {
        stubFor(delete(DroveClient.PING_API)
                        .withHeader("TestHeader", equalTo("TestValue"))
                        .willReturn(ok()));
        try(val t = new DroveHttpComponentsTransport(clientConfig(wm))) {
            Assertions.assertTrue(
                    t.execute(new DroveHttpTransport.TransportRequest(DroveClient.Method.DELETE,
                                                                      URI.create(wm.getHttpBaseUrl() + DroveClient.PING_API),
                                                                      Map.of("TestHeader", List.of("TestValue")),
                                                                      null),
                              new DroveClient.ResponseHandler<Boolean>() {
                                  @Override
                                  public Boolean defaultValue() {
                                      return false;
                                  }

                                  @Override
                                  public Boolean handle(DroveClient.Response response) {
                                      return response.statusCode() == 200;
                                  }
                              }));
            Assertions.assertFalse(
                    t.execute(new DroveHttpTransport.TransportRequest(DroveClient.Method.DELETE,
                                                                      URI.create(wm.getHttpBaseUrl() + DroveClient.PING_API),
                                                                      Map.of("TestHeader", List.of("TestValue")),
                                                                      null),
                              new DroveClient.ResponseHandler<Boolean>() {
                                  @Override
                                  public Boolean defaultValue() {
                                      return false;
                                  }

                                  @Override
                                  public Boolean handle(DroveClient.Response response) {
                                      throw new IllegalStateException("Test failure");
                                  }
                              }));
        }
    }

    private static DroveClientConfig clientConfig(WireMockRuntimeInfo wm) {
        return new DroveClientConfig(List.of(wm.getHttpBaseUrl()),
                                     Duration.ofSeconds(1),
                                     Duration.ofSeconds(1),
                                     Duration.ofSeconds(1));
    }
}