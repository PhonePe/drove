package com.phonepe.drove.common.net;

import com.google.common.base.Strings;
import com.phonepe.drove.models.common.HTTPCallSpec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.classic.methods.HttpPut;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.util.Timeout;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CancellationException;

/**
 *
 */
@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor_ = {@Inject})
public class HttpCaller {
    private static final int MAX_READABLE_OUTPUT_SIZE_BYTES = 64 * (2 ^ 10); //64KB

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
            final var credentialsProvider = new BasicCredentialsProvider();
            credentialsProvider.setCredentials(
                    new AuthScope(HttpHost.create(uri)),
                    new UsernamePasswordCredentials(httpSpec.getUsername(), httpSpec.getPassword().toCharArray()));
            context.setCredentialsProvider(credentialsProvider);
        }

        if (!Strings.isNullOrEmpty(httpSpec.getAuthHeader())) {
            request.setHeader(new BasicHeader(HttpHeaders.AUTHORIZATION, httpSpec.getAuthHeader()));
        }

        Objects.requireNonNullElseGet(httpSpec.getHeaders(), Map::<String, String>of)
                .forEach(request::setHeader);

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
        catch (CancellationException e) {
            Thread.currentThread().interrupt();
        }
        throw new IllegalStateException("Could not fetch config");
    }

}
