package com.yuno.payment_orchestrator.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    // TC_N_01 — Validation error: invalid amount
    @Test
    void shouldReturn400ForValidationError() {
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        BindingResult result = mock(BindingResult.class);
        FieldError fieldError = new FieldError("payment", "amount", "Amount must be greater than 0");

        when(ex.getBindingResult()).thenReturn(result);
        when(result.getFieldErrors()).thenReturn(List.of(fieldError));

        ResponseEntity<ErrorResponse> response = handler.handleValidationException(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("VALIDATION_ERROR", response.getBody().getErrorCode());
        assertEquals("Amount must be greater than 0", response.getBody().getMessage());
        assertNotNull(response.getBody().getTimestamp());
    }

    // TC_N_06 — Payment not found
    @Test
    void shouldReturn404ForPaymentNotFound() {
        PaymentNotFoundException ex = new PaymentNotFoundException("Payment not found: abc");

        ResponseEntity<ErrorResponse> response = handler.handleNotFound(ex);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("PAYMENT_NOT_FOUND", response.getBody().getErrorCode());
        assertEquals("Payment not found: abc", response.getBody().getMessage());
    }

    // TC_N_07 — Unsupported payment type
    @Test
    void shouldReturn400ForUnsupportedPaymentType() {
        UnsupportedPaymentTypeException ex = new UnsupportedPaymentTypeException("Unsupported payment type: BITCOIN");

        ResponseEntity<ErrorResponse> response = handler.handleUnsupportedType(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("UNSUPPORTED_PAYMENT_TYPE", response.getBody().getErrorCode());
        assertEquals("Unsupported payment type: BITCOIN", response.getBody().getMessage());
    }

    // Generic unexpected error → 500
    @Test
    void shouldReturn500ForUnexpectedException() {
        Exception ex = new RuntimeException("Unexpected error");

        ResponseEntity<ErrorResponse> response = handler.handleGeneric(ex);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("INTERNAL_ERROR", response.getBody().getErrorCode());
        assertEquals("Something went wrong", response.getBody().getMessage());
    }
}
