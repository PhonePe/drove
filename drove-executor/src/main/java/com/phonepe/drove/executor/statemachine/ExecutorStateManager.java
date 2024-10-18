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

package com.phonepe.drove.executor.statemachine;

import com.phonepe.drove.models.info.nodedata.ExecutorState;
import io.appform.signals.signals.ConsumingFireForgetSignal;

import javax.inject.Singleton;
import java.util.concurrent.atomic.AtomicReference;

/**
 *
 */
@Singleton
public class ExecutorStateManager {
    private final AtomicReference<ExecutorState> currentState = new AtomicReference<>(ExecutorState.ACTIVE);
    private final ConsumingFireForgetSignal<ExecutorState> stateChanged = new ConsumingFireForgetSignal<>();

    public void blacklist() {
        currentState.set(ExecutorState.BLACKLISTED);
        stateChanged.dispatch(ExecutorState.BLACKLISTED);
    }

    public void unblacklist() {
        currentState.set(ExecutorState.ACTIVE);
        stateChanged.dispatch(ExecutorState.ACTIVE);
    }
    public ExecutorState currentState() {
        return currentState.get();
    }

    public boolean isBlacklisted() {
        return ExecutorState.BLACKLISTED.equals(currentState.get());
    }

    public ConsumingFireForgetSignal<ExecutorState> onStateChange() {
        return stateChanged;
    }
}
