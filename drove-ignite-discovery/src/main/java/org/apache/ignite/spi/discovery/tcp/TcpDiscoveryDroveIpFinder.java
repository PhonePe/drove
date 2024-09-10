package org.apache.ignite.spi.discovery.tcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.phonepe.drove.ignite.discovery.DroveIgniteInstanceHelper;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.ignite.spi.IgniteSpiException;
import org.apache.ignite.spi.discovery.tcp.ipfinder.TcpDiscoveryIpFinderAdapter;

import java.net.InetSocketAddress;
import java.util.Collection;

@Slf4j
public class TcpDiscoveryDroveIpFinder extends TcpDiscoveryIpFinderAdapter {

    private final DrovePeerTracker peerTracker;


    @SneakyThrows
    public TcpDiscoveryDroveIpFinder(final DroveIgniteInstanceHelper droveIgniteInstanceHelper,
                                     final String discoveryPortName,
                                     final ObjectMapper mapper) {
        this.peerTracker = new DrovePeerTracker(droveIgniteInstanceHelper, discoveryPortName, mapper);
    }
    @Override
    public Collection<InetSocketAddress> getRegisteredAddresses() throws IgniteSpiException {
        return peerTracker.peers();
    }

    @Override
    public void registerAddresses(Collection<InetSocketAddress> addrs) throws IgniteSpiException {
        // No op
    }

    @Override
    public void unregisterAddresses(Collection<InetSocketAddress> addrs) throws IgniteSpiException {
        // No op
    }

}