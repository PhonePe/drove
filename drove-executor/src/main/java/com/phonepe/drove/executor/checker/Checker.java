package com.phonepe.drove.executor.checker;

import com.phonepe.drove.models.application.CheckResult;
import com.phonepe.drove.models.application.checks.CheckMode;

import java.util.concurrent.Callable;

/**
 *
 */

public interface Checker extends Callable<CheckResult> {
    CheckMode mode();

}
