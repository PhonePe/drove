package com.phonepe.drove.common.net;

import com.google.common.base.Strings;
import com.phonepe.drove.models.common.HTTPCallSpec;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.classic.methods.HttpPut;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactoryBuilder;
import org.apache.hc.client5.http.ssl.TrustAllStrategy;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.ssl.SSLContextBuilder;
import org.apache.hc.core5.util.Timeout;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;

/**
 *
 */
@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor_ = {@Inject})
public class HttpCaller {
    private static final int MAX_READABLE_OUTPUT_SIZE_BYTES = (2 ^ 20); //1MB

    private final CloseableHttpClient httpClient;

    public byte[] execute(final HTTPCallSpec httpSpec) {
        Objects.requireNonNull(httpSpec, "No config spec found");
        val port = httpSpec.getPort() != 0
                   ? httpSpec.getPort()
                   : switch (httpSpec.getProtocol()) {
                       case HTTP -> 80;
                       case HTTPS -> 443;
                   };
        val path = Objects.requireNonNullElse(httpSpec.getPath(), HTTPCallSpec.DEFAULT_PATH);
        val uri = URI.create(String.format("%s://localhost:%d%s",
                                           httpSpec.getProtocol().name().toLowerCase(),
                                           port,
                                           path));
        val verb = Objects.requireNonNullElse(httpSpec.getVerb(), HTTPCallSpec.DEFAULT_VERB);
        log.debug("Will make {} call to: {}", verb, uri);
        val request = switch (verb) {
            case GET -> new HttpGet(uri);
            case POST -> {
                val req = new HttpPost(uri);
                if (!Strings.isNullOrEmpty(httpSpec.getPayload())) {
                    req.setEntity(new StringEntity(httpSpec.getPayload()));
                }
                yield req;
            }
            case PUT -> {
                val req = new HttpPut(uri);
                if (!Strings.isNullOrEmpty(httpSpec.getPayload())) {
                    req.setEntity(new StringEntity(httpSpec.getPayload()));
                }
                yield req;
            }
        };
        request.setConfig(
                RequestConfig.custom()
                        .setResponseTimeout(Timeout.of(
                                Objects.requireNonNullElse(httpSpec.getOperationTimeout(),
                                                           HTTPCallSpec.DEFAULT_TIMEOUT)
                                        .toJavaDuration()))
                        .setConnectionRequestTimeout(Timeout.of(
                                Objects.requireNonNullElse(httpSpec.getConnectionTimeout(),
                                                           HTTPCallSpec.DEFAULT_TIMEOUT)
                                        .toJavaDuration()))
                        .build());
        val context = HttpClientContext.create();

        if (!Strings.isNullOrEmpty(httpSpec.getUsername()) && !Strings.isNullOrEmpty(httpSpec.getPassword())) {
            request.setHeader(
                    new BasicHeader(
                            HttpHeaders.AUTHORIZATION,
                            "Basic " + new String(
                                    Base64.getUrlEncoder()
                                            .encode(String.format("%s:%s",
                                                                  httpSpec.getUsername(),
                                                                  httpSpec.getPassword())
                                                            .getBytes(StandardCharsets.ISO_8859_1)))));
        }

        if (!Strings.isNullOrEmpty(httpSpec.getAuthHeader())) {
            request.setHeader(new BasicHeader(HttpHeaders.AUTHORIZATION, httpSpec.getAuthHeader()));
        }
        Objects.requireNonNullElseGet(httpSpec.getHeaders(), Map::<String, String>of)
                .forEach(request::setHeader);
        val httpClient = httpSpec.isInsecure()
                         ? createInsecureClient()
                         : this.httpClient;
        try {
            return httpClient.execute(
                    request,
                    context,
                    response -> {
                        val statusCode = response.getCode();
                        val entity = response.getEntity();
                        val responseBody =
                                Objects.requireNonNullElse(
                                        EntityUtils.toByteArray(entity, MAX_READABLE_OUTPUT_SIZE_BYTES),
                                        new byte[0]);
                        val successStatus = Objects.requireNonNullElse(httpSpec.getSuccessCodes(),
                                                                       HTTPCallSpec.DEFAULT_SUCCESS_CODES);
                        if (successStatus.contains(statusCode)) {
                            return responseBody;
                        }
                        throw new IllegalStateException("HTTP Status code mismatch. "
                                                                + "Accepted " + successStatus +
                                                                ". Received: " + response.getCode() +
                                                                ". Payload: " + new String(responseBody));
                    });
        }
        catch (IOException e) {
            throw new IllegalStateException("Could not fetch config. Received IO exception: " + e.getMessage(), e);
        }
        finally {
            if(httpSpec.isInsecure()) {
                ((CloseableHttpClient)httpClient).close(CloseMode.IMMEDIATE);
            }
        }
    }

    @SneakyThrows
    private HttpClient createInsecureClient() {
        val connectionManager = PoolingHttpClientConnectionManagerBuilder.create()
                .setSSLSocketFactory(SSLConnectionSocketFactoryBuilder.create()
                                             .setSslContext(
                                                     SSLContextBuilder.create()
                                                             .loadTrustMaterial(TrustAllStrategy.INSTANCE)
                                                             .build())
                                             .setHostnameVerifier(NoopHostnameVerifier.INSTANCE)
                                             .build())
                .build();
        return HttpClients.custom()
                .setConnectionManager(
                        connectionManager)
                .build();
    }
}
