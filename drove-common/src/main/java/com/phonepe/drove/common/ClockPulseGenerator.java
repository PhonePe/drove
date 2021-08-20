package com.phonepe.drove.common;

import io.appform.signals.signals.ConsumingFireForgetSignal;
import io.dropwizard.util.Duration;

import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

/**
 * A simple clock pulse generator. Tasks can be connected to the signal to be executed at specific intervals.
 */
public class ClockPulseGenerator {
    private final Timer timer;

    private final ConsumingFireForgetSignal<Date> pulseGenerated;

    public ClockPulseGenerator(String name, Duration initialDelay, Duration duration) {
        this.timer = new Timer(name);
        this.pulseGenerated = new ConsumingFireForgetSignal<>();
        this.timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                pulseGenerated.dispatch(new Date());
            }
        }, initialDelay.toMilliseconds(), duration.toMilliseconds());
    }


    ConsumingFireForgetSignal<Date> onPulse() {
        return pulseGenerated;
    }
}
