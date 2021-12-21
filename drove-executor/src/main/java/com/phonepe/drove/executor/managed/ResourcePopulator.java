package com.phonepe.drove.executor.managed;

import com.phonepe.drove.executor.resourcemgmt.ResourceManager;
import com.phonepe.drove.executor.resourcemgmt.NumaCtlBasedResourceLoader;
import io.dropwizard.lifecycle.Managed;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import ru.vyarus.dropwizard.guice.module.installer.order.Order;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.stream.Collectors;

/**
 * Runs 'numactl -H' to populate resource topology of the machine. This is used by controller to allocate containers
 */
@Slf4j
@Singleton
@Order(0)
public class ResourcePopulator implements Managed {
    private final ResourceManager resourceDB;
    private final NumaCtlBasedResourceLoader resourceLoader;

    @Inject
    public ResourcePopulator(
            ResourceManager resourceDB,
            NumaCtlBasedResourceLoader resourceLoader) {
        this.resourceDB = resourceDB;
        this.resourceLoader = resourceLoader;
    }

    @Override
    public void start() throws Exception {
        val process = new ProcessBuilder("numactl", "-H").start();
        var lines = Collections.<String>emptyList();
        try (val input = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            lines = input.lines()
                    .collect(Collectors.toUnmodifiableList());
        }
        val exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IllegalStateException("'numactl -H' returned: " + exitCode);
        }
        resourceDB.populateResources(resourceLoader.parseCommandOutput(lines));
    }


    @Override
    public void stop() throws Exception {
        log.info("Resource populator shut down");
    }


}
