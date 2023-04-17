package com.phonepe.drove.executor;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.Min;

/**
 *
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExecutorOptions {
    public static final long DEFAULT_MAX_OPEN_FILES = 470_000;

    public static final ExecutorOptions DEFAULT = new ExecutorOptions(true, DEFAULT_MAX_OPEN_FILES);

    private boolean cacheImages;
    @Min(0)
    long maxOpenFiles;
}
