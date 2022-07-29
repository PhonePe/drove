package com.phonepe.drove.common.coverageutils;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 *
 */
@Documented
@Retention(RUNTIME)
@Target({TYPE, METHOD})
public @interface IgnoreInJacocoGeneratedReport {
}
