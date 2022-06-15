package com.phonepe.drove.auth.model;

import lombok.Value;
import org.hibernate.validator.constraints.Length;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

/**
 *
 */
@Value
public class DroveExternalUserInfo {
    @NotEmpty
    @Length(min = 1, max = 255)
    String username;

    @NotEmpty
    @Length(min = 1, max = 255)
    String password;

    @NotNull
    DroveUserRole role;
}
