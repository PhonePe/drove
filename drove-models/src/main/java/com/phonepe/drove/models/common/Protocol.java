package com.phonepe.drove.models.common;

/**
 *
 */
public enum Protocol {
    HTTP,
    HTTPS;

    public String urlPrefix() {
        return this.name().toLowerCase();
    }
}
