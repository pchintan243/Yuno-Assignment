package com.yuno.payment_orchestrator.exception;

/**
 * Thrown when a payment lookup fails because no record exists with the given id.
 * Caught by {@link GlobalExceptionHandler} and returned as a 404 response.
 */
public class PaymentNotFoundException extends RuntimeException {

    public PaymentNotFoundException(String message) {
        super(message);
    }
}