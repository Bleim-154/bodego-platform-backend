package com.bodego.platform.shared.interfaces.rest;

import com.bodego.platform.catalog.domain.exceptions.CategoryAlreadyExistsException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import com.bodego.platform.catalog.domain.exceptions.CategoryHasProductsException;
import com.bodego.platform.catalog.domain.exceptions.ProductAlreadyExistsException;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(com.bodego.platform.shared.application.security.BusinessAccessDeniedException.class)
    public ResponseEntity<Map<String,String>> businessAccessDenied() {
        return ResponseEntity.status(403).body(Map.of("message","Forbidden"));
    }
    @ExceptionHandler(com.bodego.platform.shared.application.security.BusinessResourceNotFoundException.class)
    public ResponseEntity<Map<String,String>> resourceNotFound() {
        return ResponseEntity.status(404).body(Map.of("message","Resource not found"));
    }
    @ExceptionHandler(org.springframework.security.core.AuthenticationException.class)
    public ResponseEntity<Map<String,String>> unauthenticated() {
        return ResponseEntity.status(401).body(Map.of("message","Unauthorized"));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(
            IllegalArgumentException exception
    ) {
        return ResponseEntity
                .badRequest()
                .body(Map.of("message", exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidation(
            MethodArgumentNotValidException exception
    ) {
        var fieldError = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .findFirst();

        String message = fieldError
                .map(error -> error.getField() + ": " +
                        error.getDefaultMessage())
                .orElse("Validation error");

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(Map.of("message", message));
    }


    @ExceptionHandler(CategoryAlreadyExistsException.class)
    public ResponseEntity<Map<String, String>> handleCategoryAlreadyExists(
            CategoryAlreadyExistsException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(Map.of("message", exception.getMessage()));
    }


    @ExceptionHandler(CategoryHasProductsException.class)
    public ResponseEntity<Map<String, String>> handleCategoryHasProducts(
            CategoryHasProductsException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(Map.of("message", exception.getMessage()));
    }

    @ExceptionHandler(ProductAlreadyExistsException.class)
    public ResponseEntity<Map<String, String>> handleProductAlreadyExists(
            ProductAlreadyExistsException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(Map.of("message", exception.getMessage()));
    }


}
