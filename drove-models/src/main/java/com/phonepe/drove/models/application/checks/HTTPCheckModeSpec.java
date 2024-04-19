package com.phonepe.drove.models.application.checks;

import com.phonepe.drove.models.common.HTTPVerb;
import com.phonepe.drove.models.common.Protocol;
import io.dropwizard.util.Duration;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.Set;

/**
 *
 */
@Value
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Jacksonized
@Builder
public class HTTPCheckModeSpec extends CheckModeSpec {

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
    boolean insecure;

    public HTTPCheckModeSpec(
            Protocol protocol,
            String portName,
            String path,
            HTTPVerb verb,
            Set<Integer> successCodes,
            String payload,
            Duration connectionTimeout,
            boolean insecure) {
        super(CheckMode.HTTP);
        this.protocol = protocol;
        this.portName = portName;
        this.path = path;
        this.verb = verb;
        this.successCodes = successCodes;
        this.payload = payload;
        this.connectionTimeout = connectionTimeout;
        this.insecure = insecure;
    }

    @Override
    public <T> T accept(CheckModeSpecVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
