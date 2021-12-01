package com.phonepe.drove.executor.statemachine;

import io.appform.signals.signals.ConsumingFireForgetSignal;

import javax.inject.Singleton;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 *
 */
@Singleton
public class BlacklistingManager {
    private final AtomicBoolean blacklisted = new AtomicBoolean();
    private final ConsumingFireForgetSignal<Boolean> stateChanged = new ConsumingFireForgetSignal<>();

    public void blacklist() {
        blacklisted.set(true);
        stateChanged.dispatch(true);
    }

    public void unblacklist() {
        blacklisted.set(false);
        stateChanged.dispatch(false);
    }

    public boolean isBlacklisted() {
        return blacklisted.get();
    }

    public ConsumingFireForgetSignal<Boolean> onStateChange() {
        return stateChanged;
    }
}
