package com.jobseekercopilot.stripegateway.exception;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(BadRequestException.class)
    ResponseEntity<Map<String, String>> badRequest(BadRequestException exception) {
        return ResponseEntity.badRequest()
                .body(Map.of("error", "BAD_REQUEST", "message", exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, String>> validation(MethodArgumentNotValidException exception) {
        return ResponseEntity.badRequest()
                .body(Map.of("error", "VALIDATION_FAILED", "message", "Validation failed"));
    }

    @ExceptionHandler(StripeConfigurationException.class)
    ResponseEntity<Map<String, String>> configuration(StripeConfigurationException exception) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("error", "STRIPE_NOT_CONFIGURED", "message", exception.getMessage()));
    }

    @ExceptionHandler(RestClientException.class)
    ResponseEntity<Map<String, String>> downstream(RestClientException exception) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(Map.of("error", "DOWNSTREAM_FAILURE", "message", "Stripe gateway downstream failed"));
    }
}
