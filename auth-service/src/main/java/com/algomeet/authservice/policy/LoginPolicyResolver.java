// src/main/java/com/algomeet/authservice/policy/LoginPolicyResolver.java
package com.algomeet.authservice.policy;

import com.algomeet.authservice.config.AuthProperties;
import com.algomeet.authservice.dto.UserResponse;
import com.algomeet.authservice.enums.LoginPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LoginPolicyResolver {

    private final AuthProperties props;

    /**
     * Resolve the user's effective policy:
     * 1) use user's stored policy code if present
     * 2) otherwise fall back to application default (auth.loginTypePolicyDefault)
     */
    public LoginPolicy resolve(UserResponse user) {
        Short userCode = user.getLoginTypePolicy(); // may be null
        int effectiveCode = userCode != null
                ? userCode.intValue()
                : props.getAuth().getLoginTypePolicyDefault();
        return LoginPolicy.fromCode(effectiveCode);
    }
}
