package com.yuno.payment_orchestrator.exception;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * Standardized error response returned to API clients.
 * Every exception handler maps its exception to this structure so clients
 * receive a consistent shape regardless of what went wrong.
 */
@Getter
@Builder
public class ErrorResponse {

    /** Human-readable description of the error. */
    private String message;

    /**
     * Machine-readable error code that clients can switch on, e.g., VALIDATION_ERROR,
     * PAYMENT_NOT_FOUND, UNSUPPORTED_PAYMENT_TYPE, INTERNAL_ERROR.
     */
    private String errorCode;

    /** When the error occurred, in server local time. */
    private LocalDateTime timestamp;
}