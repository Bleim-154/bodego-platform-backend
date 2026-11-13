package com.bodego.platform.iam.infrastructure.security.login;

import org.springframework.core.env.Environment;
import java.time.Duration;

/** Local admission policy, not a distributed quota. No disable/bypass switch. */
public record LoginRateLimitPolicy(Duration window, int originAttempts, int accountAttempts,
                                   int totalAttempts, int maxKeys, int maxConcurrent) {
    public LoginRateLimitPolicy {
        if(window == null || window.compareTo(Duration.ofSeconds(1)) < 0
                || window.compareTo(Duration.ofMinutes(15)) > 0
                || originAttempts < 1 || originAttempts > 100_000
                || accountAttempts < 1 || accountAttempts > 100_000
                || totalAttempts < 1 || totalAttempts > 100_000
                || maxKeys < 2 || maxKeys > 100_000 || maxConcurrent < 1 || maxConcurrent > 32)
            throw new IllegalStateException("LOGIN_RATE_LIMIT_CONFIG");
    }

    public static LoginRateLimitPolicy from(Environment env) {
        try {
            // Preserve the socket peer. Never let a forwarded header rewrite the quota key.
            if(!"none".equalsIgnoreCase(env.getProperty("server.forward-headers-strategy", "none")))
                throw new IllegalArgumentException();
            String prefix="bodego.security.login-rate-limit.";
            return new LoginRateLimitPolicy(Duration.parse(env.getProperty(prefix+"window", "PT1M")),
                    Integer.parseInt(env.getProperty(prefix+"origin-attempts", "30")),
                    Integer.parseInt(env.getProperty(prefix+"account-attempts", "5")),
                    Integer.parseInt(env.getProperty(prefix+"total-attempts", "100")),
                    Integer.parseInt(env.getProperty(prefix+"max-keys", "4096")),
                    Integer.parseInt(env.getProperty(prefix+"max-concurrent", "4")));
        } catch(RuntimeException failure) {
            throw new IllegalStateException("LOGIN_RATE_LIMIT_CONFIG");
        }
    }
}
