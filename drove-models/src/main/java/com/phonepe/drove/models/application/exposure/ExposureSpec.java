package com.phonepe.drove.models.application.exposure;

import lombok.Value;

/**
 *
 */
@Value
public class ExposureSpec {
    String vhost;
    int portIndex;
    ExposureMode mode;
}
