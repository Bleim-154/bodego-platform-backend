package com.bodego.platform.iam.interfaces.rest;

import com.bodego.platform.iam.domain.exceptions.*;
import org.springframework.core.annotation.Order;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestControllerAdvice(assignableTypes=UsersController.class)
@Order(-10)
public class UserManagementExceptionHandler {
    @ExceptionHandler({IamAccessDeniedException.class,org.springframework.security.access.AccessDeniedException.class})
    ResponseEntity<Map<String,String>> denied() { return response(403,"Forbidden"); }
    @ExceptionHandler(org.springframework.security.core.AuthenticationException.class)
    ResponseEntity<Map<String,String>> unauthorized() { return response(401,"Unauthorized"); }
    @ExceptionHandler(ManagedUserNotFoundException.class)
    ResponseEntity<Map<String,String>> missing() { return response(404,"User not found"); }
    @ExceptionHandler(EmailAlreadyExistsException.class)
    ResponseEntity<Map<String,String>> conflict() { return response(409,"User could not be created"); }
    @ExceptionHandler(EmployeeOnlyOperationException.class)
    ResponseEntity<Map<String,String>> employeeOnly() { return response(409,"Only employee status may be changed"); }
    @ExceptionHandler(org.springframework.web.HttpMediaTypeNotSupportedException.class)
    ResponseEntity<Map<String,String>> unsupportedMedia() { return response(415,"Unsupported media type"); }
    @ExceptionHandler({InvalidIamDataException.class,org.springframework.web.bind.MethodArgumentNotValidException.class,
            org.springframework.http.converter.HttpMessageNotReadableException.class,
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class})
    ResponseEntity<Map<String,String>> invalid() { return response(400,"Invalid request"); }
    @ExceptionHandler(Exception.class)
    ResponseEntity<Map<String,String>> unexpected() { return response(500,"Internal server error"); }
    private ResponseEntity<Map<String,String>> response(int status,String message) {
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(Map.of("message",message));
    }
}
