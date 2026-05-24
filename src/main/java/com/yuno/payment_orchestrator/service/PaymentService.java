package com.yuno.payment_orchestrator.service;

import com.yuno.payment_orchestrator.dto.PaymentRequest;
import com.yuno.payment_orchestrator.dto.PaymentResponse;
import com.yuno.payment_orchestrator.exception.PaymentNotFoundException;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Defines the contract for payment operations.
 * Implementations handle idempotency, persistence, and delegation to the orchestrator.
 */
public interface PaymentService {

    /**
     * Creates a new payment or returns the existing one for the same idempotency key.
     * The payment goes through routing, processing, and state persistence.
     *
     * @param request the payment request containing amount, type, and idempotency key
     * @return the payment response with final status and assigned provider
     */
    PaymentResponse createPayment(@Valid PaymentRequest request);

    /**
     * Retrieves a single payment by its unique identifier.
     *
     * @param id the UUID of the payment
     * @return the payment response
     * @throws PaymentNotFoundException if no payment exists with the given id
     */
    PaymentResponse getPayment(String id);

    /**
     * Lists all payments with pagination support.
     *
     * @param pageable pagination parameters (page number, size, sort)
     * @return a page of payment responses ordered by creation time descending
     */
    Page<PaymentResponse> listPayments(Pageable pageable);
}