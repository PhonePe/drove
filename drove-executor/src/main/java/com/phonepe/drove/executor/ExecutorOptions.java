package com.phonepe.drove.executor;

import io.dropwizard.util.DataSize;
import io.dropwizard.util.DataSizeUnit;
import io.dropwizard.validation.DataSizeRange;
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
    public static final DataSize DEFAULT_LOG_BUFFER_SIZE = DataSize.megabytes(10);
    public static final ExecutorOptions DEFAULT = new ExecutorOptions(true,
                                                                      DEFAULT_MAX_OPEN_FILES,
                                                                      DEFAULT_LOG_BUFFER_SIZE);

    private boolean cacheImages;
    @Min(0)
    long maxOpenFiles;

    @DataSizeRange(min = 1, max = 128, unit = DataSizeUnit.MEGABYTES)
    private DataSize logBufferSize;
}
