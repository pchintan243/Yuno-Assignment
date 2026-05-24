package com.yuno.payment_orchestrator.exception;

/**
 * Thrown when a payment type (e.g. CARD, UPI) is not recognized by the routing service.
 * Caught by {@link GlobalExceptionHandler} and returned as a 400 response.
 */
public class UnsupportedPaymentTypeException extends RuntimeException {

    public UnsupportedPaymentTypeException(String message) {
        super(message);
    }
}
