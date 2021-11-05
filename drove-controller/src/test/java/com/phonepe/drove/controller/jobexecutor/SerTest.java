package com.phonepe.drove.controller.jobexecutor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.phonepe.drove.common.CommonUtils;
import com.phonepe.drove.models.operation.ApplicationOperation;
import lombok.SneakyThrows;
import lombok.val;
import org.junit.jupiter.api.Test;

/**
 *
 */
class SerTest {
    @Test
    @SneakyThrows
    void test() {
        val m = new ObjectMapper();
        CommonUtils.configureMapper(m);
        m.readValue("{\n" +
                            "    \"type\": \"CREATE\",\n" +
                            "    \"spec\": {\n" +
                            "        \"name\": \"TEST_APP\",\n" +
                            "        \"version\": 1,\n" +
                            "        \"executable\": {\n" +
                            "            \"url\": \"docker.io/santanusinha/test-service:0.1\",\n" +
                            "            \"dockerPullTimeout\": \"100 seconds\",\n" +
                            "            \"type\": \"DOCKER\"\n" +
                            "        },\n" +
                            "        \"exposedPorts\": [\n" +
                            "            {\n" +
                            "                \"port\": 3000,\n" +
                            "                \"name\": \"main\"\n" +
                            "            }\n" +
                            "        ],\n" +
                            "        \"volumes\": [],\n" +
                            "        \"type\": \"SERVICE\",\n" +
                            "        \"resources\": [\n" +
                            "            {\n" +
                            "                \"type\": \"CPU\",\n" +
                            "                \"cores\": [\n" +
                            "                    1\n" +
                            "                ]\n" +
                            "            },\n" +
                            "            {\n" +
                            "                \"type\": \"MEMORY\",\n" +
                            "                \"memoryInMB\": 512\n" +
                            "            }\n" +
                            "        ],\n" +
                            "        \"env\": {},\n" +
                            "        \"placementPolicy\": {\n" +
                            "            \"type\": \"ANY\"\n" +
                            "        },\n" +
                            "        \"instances\": 1,\n" +
                            "        \"healthcheck\": {\n" +
                            "            \"mode\": {\n" +
                            "                \"payload\": \"\",\n" +
                            "                \"verb\": \"GET\",\n" +
                            "                \"portName\": \"main\",\n" +
                            "                \"successCodes\": [\n" +
                            "                    200\n" +
                            "                ],\n" +
                            "                \"connectionTimeout\": \"1 second\",\n" +
                            "                \"protocol\": \"http\",\n" +
                            "                \"type\": \"HTTP\",\n" +
                            "                \"path\": \"/\"\n" +
                            "            },\n" +
                            "            \"initialDelay\": \"0 seconds\",\n" +
                            "            \"attempts\": 3,\n" +
                            "            \"interval\": \"3 seconds\",\n" +
                            "            \"timeout\": \"1 second\"\n" +
                            "        },\n" +
                            "        \"readiness\": {\n" +
                            "            \"interval\": \"3 seconds\",\n" +
                            "            \"timeout\": \"1 second\",\n" +
                            "            \"mode\": {\n" +
                            "                \"successCodes\": [\n" +
                            "                    200\n" +
                            "                ],\n" +
                            "                \"connectionTimeout\": \"1 second\",\n" +
                            "                \"payload\": \"\",\n" +
                            "                \"verb\": \"GET\",\n" +
                            "                \"portName\": \"main\",\n" +
                            "                \"type\": \"HTTP\",\n" +
                            "                \"path\": \"/\",\n" +
                            "                \"protocol\": \"http\"\n" +
                            "            },\n" +
                            "            \"attempts\": 3,\n" +
                            "            \"initialDelay\": \"0 seconds\"\n" +
                            "        },\n" +
                            "        \"exposureSpec\": {\n" +
                            "            \"vhost\": \"test.appform.io\",\n" +
                            "            \"portIndex\": 0,\n" +
                            "            \"mode\": \"ALL\"\n" +
                            "        }\n" +
                            "    },\n" +
                            "    \"opSpec\": {\n" +
                            "        \"timeout\": \"5m\",\n" +
                            "        \"parallelism\": 1,\n" +
                            "        \"failureStrategy\": \"STOP\"\n" +
                            "    }\n" +
                            "}", ApplicationOperation.class);
    }
}
