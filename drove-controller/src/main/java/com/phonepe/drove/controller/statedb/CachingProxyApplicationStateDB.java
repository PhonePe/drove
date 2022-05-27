package com.phonepe.drove.controller.statedb;

import com.phonepe.drove.controller.managed.LeadershipEnsurer;
import com.phonepe.drove.models.application.ApplicationInfo;
import io.appform.functionmetrics.MonitoredFunction;
import lombok.val;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.locks.StampedLock;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.phonepe.drove.common.CommonUtils.sublist;

/**
 *
 */
@Singleton
public class CachingProxyApplicationStateDB implements ApplicationStateDB {

    private final ApplicationStateDB root;
    private final Map<String, ApplicationInfo> cache = new HashMap<>();

    private final StampedLock lock = new StampedLock();

    @Inject
    public CachingProxyApplicationStateDB(@Named("StoredApplicationStateDB") ApplicationStateDB root,
                                          final LeadershipEnsurer leadershipEnsurer) {
        this.root = root;
        leadershipEnsurer.onLeadershipStateChanged().connect(this::purge);
    }

    @Override
    @MonitoredFunction
    public List<ApplicationInfo> applications(int start, int size) {
        var stamp = lock.readLock();
        try {
            if (cache.isEmpty()) {
                val status = lock.tryConvertToWriteLock(stamp);
                if (status == 0) { //Did not loc, try explicit lock
                    lock.unlockRead(stamp);
                    stamp = lock.writeLock();
                }
                else {
                    stamp = status;
                }
                loadApps();
            }
            return sublist(cache.values().stream().toList(), start, size);
        }
        finally {
            lock.unlock(stamp);
        }
    }

    @Override
    @MonitoredFunction
    public Optional<ApplicationInfo> application(String appId) {
        return applications(0, Integer.MAX_VALUE)
                .stream()
                .filter(applicationInfo -> applicationInfo.getAppId().equals(appId))
                .findAny();
    }

    @Override
    @MonitoredFunction
    public boolean updateApplicationState(String appId, ApplicationInfo applicationInfo) {
        val stamp = lock.writeLock();
        try {
            val status = root.updateApplicationState(appId, applicationInfo);
            if(status) {
                loadApps();
            }
            return status;
        }
        finally {
            lock.unlock(stamp);
        }
    }

    @Override
    @MonitoredFunction
    public boolean deleteApplicationState(String appId) {
        val stamp = lock.writeLock();
        try {
        val status = root.deleteApplicationState(appId);
        if(status) {
            loadApps();
        }
        return status;
        }
        finally {
            lock.unlock(stamp);
        }
    }

    private void purge(boolean leader) {
        val stamp = lock.writeLock();
        try {
            cache.clear();
        }
        finally {
            lock.unlock(stamp);
        }
    }
    private void loadApps() {
        cache.clear();
        cache.putAll(root.applications(0, Integer.MAX_VALUE)
                             .stream()
                             .collect(Collectors.toMap(ApplicationInfo::getAppId, Function.identity())));
    }

}
