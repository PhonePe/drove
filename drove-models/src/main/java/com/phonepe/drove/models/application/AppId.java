package com.phonepe.drove.models.application;

import lombok.Value;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.Pattern;

/**
 *
 */
@Value
public class AppId {
    @NotEmpty
    @Pattern(regexp = "[\\p{Alnum}\\-_]+")
    String name;

    @Min(0)
    int version;
}
