package com.algomeet.authservice.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.validation.annotation.Validated;

import java.util.List;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "") // binds root: auth/otp/twofa/jwt sections
public class AuthProperties {

    @NestedConfigurationProperty
    private Auth auth = new Auth();

    @NestedConfigurationProperty
    private Otp otp = new Otp();

    @NestedConfigurationProperty
    private Twofa twofa = new Twofa();

    @NestedConfigurationProperty
    private Jwt jwt = new Jwt();

    @Getter @Setter
    public static class Auth {
        private boolean requireVerifiedEmail = false;
        private boolean requireVerifiedPhone = false;
        private boolean singleActiveDevice = true;
        private int loginTypePolicyDefault = 0; // 0:any,1:mobile,2:web,3:desktop
    }

    @Getter @Setter
    public static class Otp {
        @Min(30) private int ttlSeconds = 300;
        @Min(1)  private int maxAttempts = 5;
        @Min(1)  private int resendThrottleSeconds = 60;
        @NotBlank private String pepper = "CHANGE_ME_ADD_SECRET_PEPPER";
    }

    @Getter @Setter
    public static class Twofa {
        private boolean enabled = true;
        private List<String> allowedTypes = List.of("EMAIL_OTP","SMS_OTP","TOTP","SEAMOON");
    }

    @Getter @Setter
    public static class Jwt {
        @NotBlank private String secret;
        @Min(1)  private int accessTtlMinutes = 30;
        @Min(1)  private int refreshTtlDays = 7;
    }
}
