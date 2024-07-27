package com.phonepe.drove.executor.managed;

import com.phonepe.drove.executor.resourcemgmt.ResourceManager;
import com.phonepe.drove.executor.resourcemgmt.resourceloaders.ResourceLoader;
import io.dropwizard.lifecycle.Managed;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import ru.vyarus.dropwizard.guice.module.installer.order.Order;

import javax.inject.Inject;
import javax.inject.Singleton;


@Slf4j
@Singleton
@Order(0)
public class ResourcePopulator implements Managed {
    private final ResourceManager resourceDB;
    private final ResourceLoader resourceLoader;

    @Inject
    public ResourcePopulator(
            ResourceManager resourceDB,
            ResourceLoader resourceLoader) {
        this.resourceDB = resourceDB;
        this.resourceLoader = resourceLoader;
    }

    @Override
    public void start() throws Exception {
        val resources = resourceLoader.loadSystemResources();
        if (!resources.isEmpty()) {
            log.info("Found resources:");
            resources.forEach((id, info) ->
                    log.info("    Node {} : Cores: {} Memory (MB): {} ",
                            id,
                            info.getAvailableCores().stream().sorted().toList(),
                            info.getMemoryInMB()));
        }
        else {
            log.error("No usable resources found");
        }
        resourceDB.populateResources(resources);
    }


    @Override
    public void stop() throws Exception {
        log.info("Resource populator shut down");
    }


}
