package com.phonepe.drove.common;

import lombok.Data;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 *
 */
@Data
public class ActionContext {
    private final AtomicBoolean alreadyStopped = new AtomicBoolean();

}
