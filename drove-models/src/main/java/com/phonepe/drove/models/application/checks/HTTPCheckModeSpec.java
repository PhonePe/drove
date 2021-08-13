package com.phonepe.drove.models.application.checks;

import com.phonepe.drove.models.common.HTTPVerb;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;

import java.util.Set;

/**
 *
 */
@Value
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class HTTPCheckModeSpec extends CheckModeSpec {

    String portName;
    String path;
    HTTPVerb verb;
    Set<Integer> successCodes;
    String payload;

    public HTTPCheckModeSpec(
            String portName,
            String path,
            HTTPVerb verb,
            Set<Integer> successCodes,
            String payload) {
        super(CheckMode.HTTP);
        this.portName = portName;
        this.path = path;
        this.verb = verb;
        this.successCodes = successCodes;
        this.payload = payload;
    }

    @Override
    public <T> T accept(CheckModeSpecVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
