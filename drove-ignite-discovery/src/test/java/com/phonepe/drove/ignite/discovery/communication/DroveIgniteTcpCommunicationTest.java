/*
 *  Copyright (c) 2024 Original Author(s), PhonePe India Pvt. Ltd.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.phonepe.drove.ignite.discovery.communication;

import com.phonepe.drove.ignite.discovery.config.DroveIgniteConfig;
import com.phonepe.drove.models.instance.InstancePort;
import com.phonepe.drove.models.instance.LocalInstanceInfo;
import lombok.SneakyThrows;
import lombok.val;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Map;

class DroveIgniteTcpCommunicationTest {

    @Test
    @SneakyThrows
    void testTcpCommunication() {
        val config = DroveIgniteConfig.builder()
                .communicationPortName("comm")
                .build();
        val droveTcpCommunication = new DroveIgniteTcpCommunication(config, LocalInstanceInfo.builder()
                .hostname("10.2.2.2")
                .ports(Map.of("comm", InstancePort.builder()
                        .containerPort(5050)
                        .hostPort(5051)
                        .build()))
                .build());
        Assertions.assertEquals(InetAddress.getLocalHost().getHostAddress(), droveTcpCommunication.getLocalAddress());
        Assertions.assertEquals(5050, droveTcpCommunication.getLocalPort());
        Assertions.assertEquals(0, droveTcpCommunication.getLocalPortRange());

        val addressResolver = droveTcpCommunication.getAddressResolver();
        Assertions.assertNotNull(addressResolver);

        val extAddress = addressResolver
                .getExternalAddresses(new InetSocketAddress(InetAddress.getLocalHost().getHostAddress(), 5050))
                .stream().findAny().orElse(null);

        Assertions.assertNotNull(extAddress);
        Assertions.assertEquals(new InetSocketAddress("10.2.2.2", 5051), extAddress);
    }

    @Test
    @SneakyThrows
    void testTcpCommunicationWhenCommPortDoesNotExist() {
        val config = DroveIgniteConfig.builder()
                .communicationPortName("comm1")
                .build();
        val error = Assertions.assertThrows(Exception.class, () -> new DroveIgniteTcpCommunication(config, LocalInstanceInfo.builder()
                .hostname("10.2.2.2")
                .ports(Map.of("comm", InstancePort.builder()
                        .containerPort(5050)
                        .hostPort(5051)
                        .build()))
                .build()));
        Assertions.assertTrue(error instanceof NullPointerException);
    }
}
