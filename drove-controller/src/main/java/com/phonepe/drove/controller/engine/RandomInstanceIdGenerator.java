package com.phonepe.drove.controller.engine;

import javax.inject.Singleton;
import java.util.UUID;

/**
 *
 */
@Singleton
public class RandomInstanceIdGenerator implements InstanceIdGenerator {
    @Override
    public String generate() {
        return UUID.randomUUID().toString();
    }
}
