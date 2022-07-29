package com.phonepe.drove.controller.managed;

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
@Order(15)
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
