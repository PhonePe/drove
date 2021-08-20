package com.phonepe.drove.executor;

import com.phonepe.drove.executor.checker.Checker;
import com.phonepe.drove.executor.checker.HttpChecker;
import com.phonepe.drove.models.application.checks.CheckModeSpecVisitor;
import com.phonepe.drove.models.application.checks.CheckSpec;
import com.phonepe.drove.models.application.checks.CmdCheckModeSpec;
import com.phonepe.drove.models.application.checks.HTTPCheckModeSpec;
import com.phonepe.drove.models.instance.InstanceInfo;
import lombok.experimental.UtilityClass;
import lombok.val;
import org.apache.commons.lang.NotImplementedException;

/**
 *
 */
@UtilityClass
public class Utils {
    public static Checker createChecker(
            InstanceActionContext context,
            InstanceInfo instanceInfo,
            CheckSpec readinessCheckSpec) {
        val checker = context.getInstanceSpec().getReadiness().getMode().accept(new CheckModeSpecVisitor<Checker>() {
            @Override
            public Checker visit(HTTPCheckModeSpec httpCheck) {
                return new HttpChecker(readinessCheckSpec, httpCheck, instanceInfo);
            }

            @Override
            public Checker visit(CmdCheckModeSpec cmdCheck) {
                throw new NotImplementedException("Command check is not yet implemented");
            }
        });
        return checker;
    }
}
