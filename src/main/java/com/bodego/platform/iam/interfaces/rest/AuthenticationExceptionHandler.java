package com.bodego.platform.iam.interfaces.rest;

import com.bodego.platform.iam.domain.exceptions.*;
import org.springframework.core.annotation.Order;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestControllerAdvice(assignableTypes=AuthenticationController.class)
@Order(-10)
public class AuthenticationExceptionHandler {
    @ExceptionHandler(com.bodego.platform.iam.infrastructure.security.login.LoginRateLimitExceededException.class)
    ResponseEntity<Map<String,String>> limited(com.bodego.platform.iam.infrastructure.security.login.LoginRateLimitExceededException failure) {
        return ResponseEntity.status(429).cacheControl(CacheControl.noStore())
                .header(HttpHeaders.RETRY_AFTER,Long.toString(failure.retryAfter()))
                .body(Map.of("message","Too many requests"));
    }
    @ExceptionHandler(org.springframework.security.core.AuthenticationException.class)
    ResponseEntity<Map<String,String>> unauthorized() { return response(401,"Unauthorized"); }
    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    ResponseEntity<Map<String,String>> forbidden() { return response(403,"Forbidden"); }
    @ExceptionHandler(InvalidCredentialsException.class)
    ResponseEntity<Map<String,String>> credentials() { return response(401,"Invalid credentials"); }
    @ExceptionHandler({InvalidEmailException.class,org.springframework.web.bind.MethodArgumentNotValidException.class,
            org.springframework.http.converter.HttpMessageNotReadableException.class})
    ResponseEntity<Map<String,String>> invalid() { return response(400,"Invalid request"); }
    @ExceptionHandler(Exception.class)
    ResponseEntity<Map<String,String>> unexpected() { return response(500,"Internal server error"); }
    private ResponseEntity<Map<String,String>> response(int status,String message) {
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(Map.of("message",message));
    }
}
