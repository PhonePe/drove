package com.phonepe.drove.models.application.checks;

import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;

import javax.validation.constraints.NotEmpty;

/**
 *
 */
@Value
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class CmdCheckModeSpec extends CheckModeSpec {
    @NotEmpty
    String command;

    public CmdCheckModeSpec(String command) {
        super(CheckMode.CMD);
        this.command = command;
    }

    @Override
    public <T> T accept(CheckModeSpecVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
