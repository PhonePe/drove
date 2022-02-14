package com.phonepe.drove.common.auth;

import lombok.Getter;
import lombok.experimental.UtilityClass;

import static com.phonepe.drove.common.auth.DroveUserRole.Values.*;

/**
 *
 */
public enum DroveUserRole {

    CLUSTER_NODE(DROVE_CLUSTER_NODE_ROLE),
    EXTERNAL_READ_WRITE(DROVE_EXTERNAL_READ_WRITE_ROLE),
    EXTERNAL_READ_ONLY(DROVE_EXTERNAL_READ_ONLY_ROLE),
    ;

    @Getter
    private final String value;

    @UtilityClass
    public static final class Values {
        public static final String DROVE_CLUSTER_NODE_ROLE = "DROVE_CLUSTER_NODE";
        public static final String DROVE_EXTERNAL_READ_WRITE_ROLE = "DROVE_EXTERNAL_READ_WRITE";
        public static final String DROVE_EXTERNAL_READ_ONLY_ROLE = "DROVE_EXTERNAL_READ_ONLY";
    }

    DroveUserRole(String value) {
        this.value = value;
    }
}
