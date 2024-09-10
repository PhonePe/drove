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
