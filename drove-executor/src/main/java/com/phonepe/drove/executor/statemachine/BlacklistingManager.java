package com.phonepe.drove.executor.statemachine;

import javax.inject.Singleton;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 *
 */
@Singleton
public class BlacklistingManager {
    private final AtomicBoolean blacklisted = new AtomicBoolean();

    public void blacklist() {
        blacklisted.set(true);
    }

    public void unblacklist() {
        blacklisted.set(false);
    }

    public boolean isBlacklisted() {
        return blacklisted.get();
    }
}
