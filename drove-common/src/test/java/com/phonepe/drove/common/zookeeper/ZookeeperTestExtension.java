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

package com.phonepe.drove.common.zookeeper;

import lombok.SneakyThrows;
import lombok.val;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.test.TestingCluster;
import org.junit.jupiter.api.extension.*;

import static com.phonepe.drove.common.CommonUtils.buildCurator;

/**
 *
 */
public class ZookeeperTestExtension implements BeforeEachCallback, AfterEachCallback, ParameterResolver {
    private TestingCluster testZkCluster;
    private CuratorFramework curatorFramework;

    @Override
    @SneakyThrows
    public void beforeEach(ExtensionContext context) {
        try {
            testZkCluster = new TestingCluster(1);
            testZkCluster.start();
            curatorFramework = buildCurator(new ZkConfig().setConnectionString(testZkCluster.getConnectString())
                                                    .setNameSpace("DTEST"));
            curatorFramework.start();
            curatorFramework.blockUntilConnected();
        }
        catch (Exception e) {
            testZkCluster = null;
            curatorFramework = null;
        }
    }

    @Override
    @SneakyThrows
    public void afterEach(ExtensionContext context) {
        if(null != curatorFramework) {
            curatorFramework.close();
        }
        if(null != testZkCluster) {
            testZkCluster.close();
        }
    }

    @Override
    public boolean supportsParameter(
            ParameterContext parameterContext,
            ExtensionContext extensionContext) throws ParameterResolutionException {
        return parameterContext.getParameter().getType().equals(CuratorFramework.class)
                || parameterContext.getParameter().getType().equals(TestingCluster.class);
    }

    @Override
    public Object resolveParameter(
            ParameterContext parameterContext,
            ExtensionContext extensionContext) throws ParameterResolutionException {
        val type = parameterContext.getParameter().getType();
        if(type.equals(CuratorFramework.class)) {
            return curatorFramework;
        }
        if(type.equals(TestingCluster.class)) {
            return testZkCluster;
        }
        throw new IllegalArgumentException("Cannot resolve parameter of type %s".formatted(type.getSimpleName()));
    }
}
