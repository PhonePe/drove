package com.phonepe.drove.executor.managed;

import com.phonepe.drove.executor.AbstractExecutorEngineEnabledTestBase;
import com.phonepe.drove.executor.resourcemgmt.ResourceConfig;
import com.phonepe.drove.executor.resourcemgmt.NumaCtlBasedResourceLoader;
import lombok.SneakyThrows;
import lombok.val;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 *
 */
class ResourcePopulatorTest extends AbstractExecutorEngineEnabledTestBase {

    @Test
    @SneakyThrows
    void testResourcePopulator() {
        val resourceConfig = new ResourceConfig();
        resourceConfig.setOsCores(Set.of());
        resourceConfig.setExposedMemPercentage(90);
        resourceConfig.setTags(Set.of("test-machine"));
        val resourcePopulator = new ResourcePopulator(resourceDB, new NumaCtlBasedResourceLoader(resourceConfig));
        resourcePopulator.start();
        val currentState = resourceDB.currentState();
        assertFalse(currentState.getCpu().getFreeCores().isEmpty());
        assertFalse(currentState.getMemory().getFreeMemory().isEmpty());
        resourcePopulator.stop();
    }

}