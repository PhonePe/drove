package com.phonepe.drove.auth.model;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import static com.phonepe.drove.auth.model.DroveUserRole.Values.*;

/**
 *
 */
public enum DroveUserRole {

    CLUSTER_NODE(DROVE_CLUSTER_NODE_ROLE),
    DROVE_APPLICATION_INSTANCE(DROVE_APPLICATION_INSTANCE_ROLE),
    EXTERNAL_READ_WRITE(DROVE_EXTERNAL_READ_WRITE_ROLE),
    EXTERNAL_READ_ONLY(DROVE_EXTERNAL_READ_ONLY_ROLE),
    ;

    @Getter
    private final String value;

    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static final class Values {
        public static final String DROVE_CLUSTER_NODE_ROLE = "DROVE_CLUSTER_NODE";
        public static final String DROVE_APPLICATION_INSTANCE_ROLE = "DROVE_APPLICATION_INSTANCE";
        public static final String DROVE_EXTERNAL_READ_WRITE_ROLE = "DROVE_EXTERNAL_READ_WRITE";
        public static final String DROVE_EXTERNAL_READ_ONLY_ROLE = "DROVE_EXTERNAL_READ_ONLY";
    }

    DroveUserRole(String value) {
        this.value = value;
    }
}
