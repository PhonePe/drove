package com.phonepe.drove.models.application.checks;

import com.phonepe.drove.models.common.HTTPVerb;
import io.dropwizard.util.Duration;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.Set;

/**
 *
 */
@Value
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class HTTPCheckModeSpec extends CheckModeSpec {

    public enum Protocol {
        HTTP,
        HTTPS
    }

    Protocol protocol;
    @NotEmpty
    String portName;
    @NotEmpty
    String path;
    @NotNull
    HTTPVerb verb;
    @NotEmpty
    @NotNull
    Set<Integer> successCodes;
    String payload;
    Duration connectionTimeout;

    public HTTPCheckModeSpec(
            Protocol protocol,
            String portName,
            String path,
            HTTPVerb verb,
            Set<Integer> successCodes,
            String payload,
            Duration connectionTimeout) {
        super(CheckMode.HTTP);
        this.protocol = protocol;
        this.portName = portName;
        this.path = path;
        this.verb = verb;
        this.successCodes = successCodes;
        this.payload = payload;
        this.connectionTimeout = connectionTimeout;
    }

    @Override
    public <T> T accept(CheckModeSpecVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
