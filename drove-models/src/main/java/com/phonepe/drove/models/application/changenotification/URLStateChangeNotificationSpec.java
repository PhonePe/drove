package com.phonepe.drove.models.application.changenotification;

import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;
import org.hibernate.validator.constraints.URL;

import javax.validation.constraints.NotEmpty;

/**
 *
 */
@Value
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class URLStateChangeNotificationSpec extends StateChangeNotificationSpec {

    @NotEmpty
    @URL
    String callbackURL;

    public URLStateChangeNotificationSpec(String callbackURL) {
        super(StateChangeNotifierType.CALLBACK);
        this.callbackURL = callbackURL;
    }

    @Override
    public <T> T accept(StateChangeNotificationSpecVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
