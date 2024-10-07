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
import lombok.val;
import org.apache.ignite.configuration.AddressResolver;
import org.apache.ignite.configuration.BasicAddressResolver;
import org.apache.ignite.spi.communication.tcp.TcpCommunicationSpi;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Objects;

public class DroveIgniteTcpCommunication extends TcpCommunicationSpi {

    public DroveIgniteTcpCommunication(final DroveIgniteConfig config,
                                       final LocalInstanceInfo localInstanceInfo) throws UnknownHostException {
        val containerAddress = InetAddress.getLocalHost().getHostAddress();
        val portName = config.getCommunicationPortName();
        Objects.requireNonNull(portName, "DroveTcpCommunication portName cannot be empty");

        val portInfo = localInstanceInfo.getPorts().get(portName);
        Objects.requireNonNull(portInfo, String.format("DroveTcpCommunication portInfo cannot be null for port: %s", portName));

        this.setAddressResolver(getAddressResolver(localInstanceInfo.getHostname(), portInfo, containerAddress));
        this.setLocalAddress(containerAddress);
        this.setLocalPort(portInfo.getContainerPort());
        this.setFilterReachableAddresses(true);
        this.setLocalPortRange(0);
    }

    private AddressResolver getAddressResolver(final String hostName,
                                               final InstancePort instancePort,
                                               final String containerAddress) throws UnknownHostException {
        val addrMap = new HashMap<String, String>();
        addrMap.put(containerAddress + ":" + instancePort.getContainerPort(),
                InetAddress.getByName(hostName).getHostAddress() + ":" + instancePort.getHostPort());
        return new BasicAddressResolver(addrMap);
    }
}
