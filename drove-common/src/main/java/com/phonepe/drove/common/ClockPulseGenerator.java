package com.phonepe.drove.common;

import io.appform.signals.signals.ConsumingParallelSignal;
import lombok.extern.slf4j.Slf4j;

import java.io.Closeable;
import java.io.IOException;
import java.time.Duration;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

/**
 * A simple clock pulse generator. Tasks can be connected to the signal to be executed at specific intervals.
 */
@Slf4j
public class ClockPulseGenerator implements Closeable {
    private final Timer timer;

    private final ConsumingParallelSignal<Date> pulseGenerated;

    public ClockPulseGenerator(String name, Duration initialDelay, Duration duration) {
        this.timer = new Timer(name);
        this.pulseGenerated = new ConsumingParallelSignal<>();
        this.timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                pulseGenerated.dispatch(new Date());
            }
        }, initialDelay.toMillis(), duration.toMillis());
    }


    public ConsumingParallelSignal<Date> onPulse() {
        return pulseGenerated;
    }

    @Override
    public void close() throws IOException {
        timer.cancel();
    }
}
