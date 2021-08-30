package com.phonepe.drove.executor.recovery;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

/**
 *
 */
class InstanceRecoveryTest {

    @Test
    void test() {
        new InstanceRecovery(new ObjectMapper()).recoverState();
    }

}