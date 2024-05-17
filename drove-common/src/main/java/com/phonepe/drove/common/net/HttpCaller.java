package com.phonepe.drove.common.net;

import com.google.common.base.Strings;
import com.phonepe.drove.models.common.HTTPCallSpec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.util.Timeout;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;

import static com.phonepe.drove.common.CommonUtils.buildRequest;

/**
 *
 */
@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor_ = {@Inject})
public class HttpCaller {
    private static final int MAX_READABLE_OUTPUT_SIZE_BYTES = (2 ^ 20); //1MB

    private final CloseableHttpClient httpClient;
    private final @Named("insecure") CloseableHttpClient insecureHttpClient;

    public byte[] execute(final HTTPCallSpec httpSpec) {
        Objects.requireNonNull(httpSpec, "No config spec found");
        val uri = buildUri(httpSpec);
        val verb = Objects.requireNonNullElse(httpSpec.getVerb(), HTTPCallSpec.DEFAULT_VERB);
        log.debug("Will make {} call to: {}", verb, uri);
        val request = buildRequest(verb, uri, httpSpec.getPayload());
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
        val clientForUse = httpSpec.isInsecure()
                         ? insecureHttpClient
                         : this.httpClient;
        try {
            return clientForUse.execute(
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
                ((CloseableHttpClient)clientForUse).close(CloseMode.IMMEDIATE);
            }
        }
    }

    @SuppressWarnings("java:S2275")
    private static URI buildUri(HTTPCallSpec httpSpec) {
        val port = httpSpec.getPort() != 0
                   ? httpSpec.getPort()
                   : switch (httpSpec.getProtocol()) {
                       case HTTP -> 80;
                       case HTTPS -> 443;
                   };
        val path = Objects.requireNonNullElse(httpSpec.getPath(), HTTPCallSpec.DEFAULT_PATH);
        return URI.create(String.format("%s://localhost:%d%s",
                                        httpSpec.getProtocol().name().toLowerCase(),
                                        port,
                                        path));
    }
}
