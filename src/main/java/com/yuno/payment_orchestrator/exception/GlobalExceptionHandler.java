package com.yuno.payment_orchestrator.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    // Validation Errors — returned when @Valid fails on request bodies
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));

        return ResponseEntity.badRequest().body(
                ErrorResponse.builder()
                        .message(message)
                        .errorCode("VALIDATION_ERROR")
                        .timestamp(LocalDateTime.now())
                        .build()
        );
    }

    // Payment Not Found — returned when a payment id does not exist
    @ExceptionHandler(PaymentNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(PaymentNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                ErrorResponse.builder()
                        .message(ex.getMessage())
                        .errorCode("PAYMENT_NOT_FOUND")
                        .timestamp(LocalDateTime.now())
                        .build()
        );
    }

    // Unsupported Payment Type — returned when a payment type (e.g., CARD, UPI) is not recognized
    @ExceptionHandler(UnsupportedPaymentTypeException.class)
    public ResponseEntity<ErrorResponse> handleUnsupportedType(UnsupportedPaymentTypeException ex) {
        return ResponseEntity.badRequest().body(
                ErrorResponse.builder()
                        .message(ex.getMessage())
                        .errorCode("UNSUPPORTED_PAYMENT_TYPE")
                        .timestamp(LocalDateTime.now())
                        .build()
        );
    }

    // Catch-all — handles any unexpected exceptions to avoid leaking stack traces to clients
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                ErrorResponse.builder()
                        .message("Something went wrong")
                        .errorCode("INTERNAL_ERROR")
                        .timestamp(LocalDateTime.now())
                        .build()
        );
    }
}
