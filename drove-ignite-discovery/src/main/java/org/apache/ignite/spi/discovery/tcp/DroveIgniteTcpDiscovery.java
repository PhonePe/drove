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

package org.apache.ignite.spi.discovery.tcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.phonepe.drove.ignite.discovery.DroveIgniteInstanceHelper;
import com.phonepe.drove.ignite.discovery.LocalInstanceTracker;
import com.phonepe.drove.ignite.discovery.config.DroveIgniteConfig;
import com.phonepe.drove.models.instance.InstancePort;
import com.phonepe.drove.models.instance.LocalInstanceInfo;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.ignite.configuration.AddressResolver;
import org.apache.ignite.configuration.BasicAddressResolver;
import org.apache.ignite.lang.IgniteProductVersion;
import org.apache.ignite.spi.discovery.tcp.internal.TcpDiscoveryNode;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
public class DroveIgniteTcpDiscovery extends TcpDiscoverySpi {

    public DroveIgniteTcpDiscovery(final DroveIgniteConfig droveIgniteConfig,
                                   final LocalInstanceInfo localInstanceInfo,
                                   final DroveIgniteInstanceHelper droveIgniteInstanceHelper,
                                   final ObjectMapper mapper) throws UnknownHostException {
        val containerAddress = InetAddress.getLocalHost().getHostAddress();
        val portName = droveIgniteConfig.getDiscoveryPortName();
        Objects.requireNonNull(portName, "DroveTcpDiscovery portName cannot be empty");

        val portInfo = localInstanceInfo.getPorts().get(portName);
        Objects.requireNonNull(portInfo, String.format("DroveTcpDiscovery portInfo cannot be null for port: %s", portName));

        val containerPort = portInfo.getContainerPort();
        this.setIpFinder(new TcpDiscoveryDroveIpFinder(droveIgniteInstanceHelper, portName, mapper));
        this.setAddressResolver(getAddressResolver(localInstanceInfo.getHostname(), portInfo, containerAddress));
        this.setLocalAddress(containerAddress);
        this.setLocalPort(containerPort);
        this.setLocalPortRange(0);
    }

    // Not adding local socket addresses here so that the connection can be made to external addresses only
    @Override
    LinkedHashSet<InetSocketAddress> getNodeAddresses(TcpDiscoveryNode node) {
        val res = new LinkedHashSet<InetSocketAddress>();
        Collection<InetSocketAddress> extAddrs = getExtAddresses(node);
        if (extAddrs != null)
            res.addAll(extAddrs);

        return res;
    }

    // Not adding local socket addresses here so that the connection can be made to external addresses only
    @Override
    LinkedHashSet<InetSocketAddress> getNodeAddresses(TcpDiscoveryNode node, boolean sameHost) {
        val res = new LinkedHashSet<InetSocketAddress>();
        val lastAddr = node.lastSuccessfulAddress();
        if (lastAddr != null)
            res.add(lastAddr);

        Collection<InetSocketAddress> extAddrs = getExtAddresses(node);

        if (extAddrs != null)
            res.addAll(extAddrs);

        return res;
    }

    private Collection<InetSocketAddress> getExtAddresses(final TcpDiscoveryNode node) {
        return node.attribute(createSpiAttributeName(ATTR_EXT_ADDRS));
    }

    private AddressResolver getAddressResolver(final String hostName,
                                               final InstancePort instancePort,
                                               final String containerAddress) throws UnknownHostException {
        val addrMap = new HashMap<String, String>();
        addrMap.put(containerAddress + ":" + instancePort.getContainerPort(),
                InetAddress.getByName(hostName).getHostAddress() + ":" + instancePort.getHostPort());
        return new BasicAddressResolver(addrMap);
    }


    @Override
    public void setNodeAttributes(Map<String, Object> attrs, IgniteProductVersion ver) {
        assert locNodeAttrs == null;
        assert locNodeVer == null;

        if (log.isDebugEnabled()) {
            log.debug("Node attributes to set: " + attrs);
            log.debug("Node version to set: " + ver);
        }

        attrs.put("DroveIgniteTcpCommunication.comm.tcp.addrs", List.of());
        locNodeAttrs = attrs;
        locNodeVer = ver;
    }
}
