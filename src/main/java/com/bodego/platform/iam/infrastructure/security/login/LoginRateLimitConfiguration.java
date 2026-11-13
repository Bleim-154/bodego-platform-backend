package com.bodego.platform.iam.infrastructure.security.login;

import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;

@Configuration
public class LoginRateLimitConfiguration {
    @Bean static org.springframework.beans.factory.config.BeanFactoryPostProcessor loginRateLimitGuard(Environment environment) {
        return factory -> LoginRateLimitPolicy.from(environment);
    }
    @Bean LoginRateLimitPolicy loginRateLimitPolicy(Environment environment) {
        return LoginRateLimitPolicy.from(environment);
    }
    @Bean LoginRateLimiter loginRateLimiter(LoginRateLimitPolicy policy) {
        return new LoginRateLimiter(policy,System::nanoTime);
    }
}
