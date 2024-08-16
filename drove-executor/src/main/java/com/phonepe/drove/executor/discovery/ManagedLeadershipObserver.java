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

package com.phonepe.drove.executor.discovery;

import com.phonepe.drove.common.discovery.leadership.LeadershipObserver;
import com.phonepe.drove.models.info.nodedata.ControllerNodeData;
import io.dropwizard.lifecycle.Managed;
import lombok.extern.slf4j.Slf4j;
import ru.vyarus.dropwizard.guice.module.installer.order.Order;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Optional;

/**
 *
 */
@Order(30)
@Slf4j
@Singleton
public class ManagedLeadershipObserver implements Managed {

    private final LeadershipObserver leadershipObserver;

    @Inject
    public ManagedLeadershipObserver(LeadershipObserver leadershipObserver) {
        this.leadershipObserver = leadershipObserver;
    }

    public Optional<ControllerNodeData> leader() {
        return leadershipObserver.leader();
    }

    @Override
    public void start() throws Exception {
        leadershipObserver.start();
    }

    @Override
    public void stop() throws Exception {
        leadershipObserver.stop();
    }
}
