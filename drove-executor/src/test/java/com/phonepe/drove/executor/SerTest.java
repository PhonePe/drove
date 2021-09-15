package com.phonepe.drove.executor;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.phonepe.drove.common.CommonUtils;
import com.phonepe.drove.common.model.ExecutorMessageType;
import com.phonepe.drove.common.model.Message;
import com.phonepe.drove.common.model.MessageHeader;
import com.phonepe.drove.common.model.executor.StartInstanceMessage;
import com.phonepe.drove.models.application.requirements.CPURequirement;
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
        final ObjectMapper objectMapper = new ObjectMapper();
        CommonUtils.configureMapper(objectMapper);
        objectMapper.readValue("{\n" +
                                       "            \"type\" : \"CPU\",\n" +
                                       "            \"count\" : 1\n" +
                                       "         }", CPURequirement.class);
    }

    @Test
    @SneakyThrows
    void test2() {
        val str = "{\n" +
                "   \"spec\" : {\n" +
                "      \"executable\" : {\n" +
                "         \"url\" : \"docker.io/santanusinha/test-service:0.1\",\n" +
                "         \"dockerPullTimeout\" : \"100 seconds\",\n" +
                "         \"type\" : \"DOCKER\"\n" +
                "      },\n" +
                "      \"readiness\" : {\n" +
                "         \"initialDelay\" : \"0 seconds\",\n" +
                "         \"timeout\" : \"1 second\",\n" +
                "         \"attempts\" : 3,\n" +
                "         \"mode\" : {\n" +
                "            \"portName\" : \"main\",\n" +
                "            \"type\" : \"HTTP\",\n" +
                "            \"connectionTimeout\" : \"1 second\",\n" +
                "            \"path\" : \"/\",\n" +
                "            \"payload\" : \"\",\n" +
                "            \"successCodes\" : [\n" +
                "               200\n" +
                "            ],\n" +
                "            \"protocol\" : \"http\",\n" +
                "            \"verb\" : \"GET\"\n" +
                "         },\n" +
                "         \"interval\" : \"3 seconds\"\n" +
                "      },\n" +
                "      \"env\" : {},\n" +
                "      \"instanceId\" : \"7d26e732-79e0-4861-9049-c5464ce2ce2b\",\n" +
                "      \"appId\" : {\n" +
                "         \"name\" : \"test\",\n" +
                "         \"version\" : 1\n" +
                "      },\n" +
                "      \"resources\" : [\n" +
                "         {\n" +
                "            \"type\" : \"CPU\",\n" +
                "            \"count\" : 1\n" +
                "         },\n" +
                "         {\n" +
                "            \"sizeInMB\" : 512,\n" +
                "            \"type\" : \"MEMORY\"\n" +
                "         }\n" +
                "      ],\n" +
                "      \"healthcheck\" : {\n" +
                "         \"interval\" : \"3 seconds\",\n" +
                "         \"mode\" : {\n" +
                "            \"payload\" : \"\",\n" +
                "            \"path\" : \"/\",\n" +
                "            \"successCodes\" : [\n" +
                "               200\n" +
                "            ],\n" +
                "            \"connectionTimeout\" : \"1 second\",\n" +
                "            \"protocol\" : \"http\",\n" +
                "            \"verb\" : \"GET\",\n" +
                "            \"type\" : \"HTTP\",\n" +
                "            \"portName\" : \"main\"\n" +
                "         },\n" +
                "         \"attempts\" : 3,\n" +
                "         \"timeout\" : \"1 second\",\n" +
                "         \"initialDelay\" : \"0 seconds\"\n" +
                "      },\n" +
                "      \"volumes\" : [],\n" +
                "      \"ports\" : [\n" +
                "         {\n" +
                "            \"port\" : 3000,\n" +
                "            \"name\" : \"main\"\n" +
                "         }\n" +
                "      ]\n" +
                "   },\n" +
                "   \"header\" : {\n" +
                "      \"messageTime\" : \"1630389926000\",\n" +
                "      \"senderType\" : \"CONTROLLER\",\n" +
                "      \"direction\" : \"REQUEST\",\n" +
                "      \"id\" : \"m1\"\n" +
                "   },\n" +
                "   \"type\" : \"START_INSTANCE\"\n" +
                "}\n";
        final ObjectMapper objectMapper = new ObjectMapper();
        CommonUtils.configureMapper(objectMapper);
        val spec = new StartInstanceMessage(MessageHeader.controllerRequest(), TestingUtils.testSpec());
        val m = objectMapper.writeValueAsString(spec);
        val msg = objectMapper.readValue(m, new TypeReference<Message<ExecutorMessageType>>() {
        });
        System.out.println(msg);
    }
}
