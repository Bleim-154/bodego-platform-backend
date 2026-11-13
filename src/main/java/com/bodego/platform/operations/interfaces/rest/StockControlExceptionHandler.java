package com.bodego.platform.operations.interfaces.rest;

import com.bodego.platform.operations.domain.exceptions.StockControlConflictException;
import com.bodego.platform.shared.application.security.BusinessAccessDeniedException;
import com.bodego.platform.shared.application.security.BusinessResourceNotFoundException;
import org.springframework.core.annotation.Order;
import org.springframework.http.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.Map;

/** Scoped contracts: unrelated contexts retain their existing exception handling. */
@RestControllerAdvice(assignableTypes = {StockAdjustmentsController.class, CorrectionRequestsController.class})
@Order(-10)
public class StockControlExceptionHandler extends ResponseEntityExceptionHandler {
    @ExceptionHandler(StockControlConflictException.class)
    ResponseEntity<Object> conflict(StockControlConflictException exception) {
        // Messages originate in specific domain exceptions, never in request data or SQL.
        return response(409, exception.getMessage());
    }

    @ExceptionHandler({BusinessAccessDeniedException.class, AccessDeniedException.class})
    ResponseEntity<Object> forbidden() { return response(403, "Forbidden"); }

    @ExceptionHandler(BusinessResourceNotFoundException.class)
    ResponseEntity<Object> missing() { return response(404, "Resource not found"); }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<Object> unauthorized() { return response(401, "Unauthorized"); }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Object> invalid() { return response(400, "Invalid request"); }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Object> unexpected() { return response(500, "Internal server error"); }

    @Override
    protected ResponseEntity<Object> createResponseEntity(Object body, HttpHeaders headers,
                                                          HttpStatusCode status, WebRequest request) {
        // Preserve framework status/headers (400, 405, 415, etc.) without exposing exception details.
        var known = HttpStatus.resolve(status.value());
        String message = status.is5xxServerError() ? "Internal server error"
                : status.value() == 400 ? "Invalid request"
                : known == null ? "Request failed" : known.getReasonPhrase();
        return new ResponseEntity<>(Map.of("message", message), headers, status);
    }

    private ResponseEntity<Object> response(int status, String message) {
        return ResponseEntity.status(status).body(Map.of("message", message));
    }
}
