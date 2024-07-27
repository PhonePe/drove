package com.phonepe.drove.executor.managed;

import com.phonepe.drove.executor.AbstractExecutorEngineEnabledTestBase;
import com.phonepe.drove.executor.resourcemgmt.ResourceManager;
import com.phonepe.drove.executor.resourcemgmt.resourceloaders.ResourceLoader;
import lombok.SneakyThrows;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import java.util.Map;
import java.util.Set;


/**
 *
 */
class ResourcePopulatorTest extends AbstractExecutorEngineEnabledTestBase {

    @Test
    @SneakyThrows
    void testResourcePopulatorLoadsPopulatedResource() {
        val resourceDB = Mockito.mock(ResourceManager.class);
        val resourceLoader = Mockito.mock(ResourceLoader.class);
        val availableCores0 = Set.of(1, 2, 3, 4);
        val discoveredNodeInfo = ResourceManager.NodeInfo.from(availableCores0, 1000);
        val resource = Map.of(0,
                              discoveredNodeInfo);
        Mockito.when(resourceLoader.loadSystemResources()).thenReturn(resource);
        val resourcePopulator = new ResourcePopulator(resourceDB, resourceLoader);
        resourcePopulator.start();
        Mockito.verify(resourceDB, Mockito.times(1))
                .populateResources(Map.of(0, discoveredNodeInfo));
        resourcePopulator.stop();
    }


}