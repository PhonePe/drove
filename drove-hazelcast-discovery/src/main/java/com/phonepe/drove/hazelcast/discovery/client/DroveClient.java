package com.phonepe.drove.hazelcast.discovery.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.phonepe.drove.hazelcast.discovery.exception.DroveException;
import feign.Feign;
import feign.Logger.Level;
import feign.RequestInterceptor;
import feign.Response;
import feign.codec.ErrorDecoder;
import feign.jackson.JacksonDecoder;
import feign.jackson.JacksonEncoder;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.val;

import static java.util.Arrays.asList;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class DroveClient {

    static class DroveErrorDecoder implements ErrorDecoder {
        @Override
        public Exception decode(String methodKey, Response response) {
            return new DroveException(response.status(), response.reason());
        }
    }

    public static Drove getInstance(ObjectMapper objectMapper, String endpoint, RequestInterceptor... interceptors) {
        val b = Feign.builder()
                .encoder(new JacksonEncoder(objectMapper))
                .decoder(new JacksonDecoder(objectMapper))
                .logLevel(Level.FULL)
                .errorDecoder(new DroveErrorDecoder());
        if (interceptors!=null)
            b.requestInterceptors(asList(interceptors));
        return b.target(Drove.class, endpoint);
    }
}
