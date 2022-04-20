package com.phonepe.drove.controller.engine;

import java.util.UUID;

/**
 *
 */
public class RandomInstanceIdGenerator implements InstanceIdGenerator {
    @Override
    public String generate() {
        return UUID.randomUUID().toString();
    }
}
