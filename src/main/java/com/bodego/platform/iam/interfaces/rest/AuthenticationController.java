package com.bodego.platform.iam.interfaces.rest;

import com.bodego.platform.iam.application.internal.outboundservices.tokens.AccessTokenService;
import com.bodego.platform.iam.domain.model.commands.SignInCommand;
import com.bodego.platform.iam.domain.model.valueobjects.EmailAddress;
import com.bodego.platform.iam.domain.services.AuthenticationCommandService;
import com.bodego.platform.shared.application.security.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.Arrays;
import com.bodego.platform.iam.infrastructure.security.login.LoginRateLimiter;
import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthenticationController {
    public record LoginResource(@NotBlank @Size(max=254) String email,
                                @NotBlank @Size(max=72) String password) {
        @Override public String toString() { return "LoginResource[REDACTED]"; }
    }
    private final AuthenticationCommandService authentication;
    private final AccessTokenService tokens;
    private final CurrentIdentity current;
    private final LoginRateLimiter loginRateLimiter;
    public AuthenticationController(AuthenticationCommandService authentication,AccessTokenService tokens,CurrentIdentity current,
                                    LoginRateLimiter loginRateLimiter) {
        this.authentication=authentication; this.tokens=tokens; this.current=current; this.loginRateLimiter=loginRateLimiter;
    }
    @PostMapping("/login")
    public ResponseEntity<AccessTokenService.IssuedAccessToken> login(@Valid @RequestBody LoginResource resource, HttpServletRequest request) {
        var email=new EmailAddress(resource.email());
        try(var permit=loginRateLimiter.acquire(request.getRemoteAddr(),email)) {
            char[] password=resource.password().toCharArray();
            try(var command=new SignInCommand(email.value(),password)) {
                return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(tokens.issue(authentication.handle(command)));
            } finally {Arrays.fill(password,'\0');}
        }
    }
    @GetMapping("/me")
    public ResponseEntity<AuthenticatedIdentity> me() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(current.require());
    }
}
