package com.phonepe.drove.hazelcast.discovery.client;

import java.util.List;

public class DroveClientProvider {

    private final List<Drove> droveEndpoints;
    private int currentActiveHostIndex;

    public DroveClientProvider(List<Drove> droveEndpoints) {
        this.droveEndpoints = droveEndpoints;
        this.currentActiveHostIndex = 0;
    }

    public Drove getCurrentActiveClient() {
        return droveEndpoints.get(currentActiveHostIndex);
    }

    public int size() {
        return droveEndpoints.size();
    }

    public void incrementActiveHostIndex() {
        currentActiveHostIndex++;
        if (currentActiveHostIndex >= droveEndpoints.size()) {
            currentActiveHostIndex = 0;
        }
    }
}
